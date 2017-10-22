package ru.pharmrus.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

import com.intersys.jdbc.*;

public class AptekaPlus {
    private final String url;
    private final String driverClass = "com.intersys.jdbc.CacheDriver";
    private final String userName;
    private final String password;

    private static final SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.YYYY HH:mm");

    private AptekaPlus(String url, String userName, String password) {
        this.url = url;
        this.userName = userName;
        this.password = password;
    }

    private Connection connect() throws ClassNotFoundException, SQLException {
        CacheDataSource ds = new CacheDataSource();
        ds.setURL(url);
        ds.setUser(userName);
        ds.setPassword(password);
        Connection con = ds.getConnection();
        debug ("Connected.");
        return con;
    }

    private static void debug(String message) {
        Date dt = new Date();
        String sDate = fmt.format(dt);
        System.out.printf("%s: %s\n", sDate, message);
    }

    static Query loadQuery(String queryName) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            InputStream is = AptekaPlus.class.getResourceAsStream("/queries.xml");
            InputStreamReader isr = new InputStreamReader(is, "utf-8");
            BufferedReader br = new BufferedReader(isr);

            StringBuffer sb = new StringBuffer("");
            br.lines().forEach(s -> sb.append(s).append("\n"));

            Document doc = dBuilder.parse(new ByteArrayInputStream(sb.toString().getBytes("utf-8")));
            XPathFactory xpf = XPathFactory.newInstance();
            XPath xpath = xpf.newXPath();

            String xpathQuery = String.format("/Queries/Query[@name='%s']", queryName);
            Element element = (Element) xpath.evaluate(xpathQuery, doc, XPathConstants.NODE);
            Query query = new Query();

            query.setDependOn(element.getAttribute("dependsOn"));
            String params = element.getAttribute("parameters");
            if (params != null)
                query.setParameters(params.split(","));

            query.setName(queryName);
            query.setQuery(getCharacterDataFromElement (element));
            return query;
        }
        catch (Exception e) {
            debug (e.getMessage());
        }

        return null;
    }

    private static String getCharacterDataFromElement(Element e) {
        NodeList list = e.getChildNodes();
        String data;

        for(int index = 0; index < list.getLength(); index++){
            if(list.item(index) instanceof CharacterData){
                CharacterData child = (CharacterData) list.item(index);
                data = child.getData();

                if(data != null && data.trim().length() > 0)
                    return child.getData();
            }
        }
        return "";
    }

    private List<String> prepareMetaData (ResultSet rSet) throws SQLException {
        ResultSetMetaData md = rSet.getMetaData();
        int len = md.getColumnCount();
        List<String> columns = new ArrayList<>(len);
        for (int i=1;i<=len;i++) {
            columns.add (md.getColumnName(i));
        }

        debug ("loaded columns: " + columns);
        return columns;
    }

    private String getNdsString (String colValue) throws SQLException {
        if (colValue != null) {
            colValue = colValue.replaceAll("НДС[^\\d]+(\\d+\\%).*", "$1");
            colValue = colValue.replaceAll("(Без НДС).*", "$1");
        }

        return colValue;
    }

    private Map<String, Object> fillRow (List<String> columns, ResultSet rSet) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        for (int i=1;i<=columns.size();i++) {
            String value = rSet.getString(i);
            if (columns.get(i-1).toLowerCase().contains("nds")) {
                value = getNdsString (value);
            }

            row.put(columns.get(i-1), value);
        }

        return row;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, ?>> loadGoods(String queryName) {
        // load XML query
        debug ("loading query from resource...");
        Query query = loadQuery(queryName);
        if (query == null) {
            debug ("Query with name: " + queryName + " not found");
            return Collections.EMPTY_LIST;
        }

        if (query.getDependOn() != null && !query.getDependOn().isEmpty()) {
            // There is only one dependency level allowed
            Query parent = loadQuery(query.getDependOn());
            if (parent == null) {
                debug ("Parent with name: " + query.getDependOn() + " not found");
                return Collections.EMPTY_LIST;
            }

            parent.setChild(query);
            query = parent;
        }

        debug ("Ok. Trying to connect to " + url + "...");

        try (Connection con = connect();
             PreparedStatement stmt = con.prepareStatement(query.getQuery());
             ResultSet rSet = stmt.executeQuery()) {
            debug ("Statement executed.");
            List<String> columns = prepareMetaData(rSet);

            List<Map<String, ?>> rows = new ArrayList<>();
            while (rSet.next()) {
                Map<String, Object> row = fillRow(columns, rSet);

                // Execute subquery if exists
                if (query.getChild() != null) {
                    try (PreparedStatement childStmt = con.prepareStatement(query.getChild().getQuery())) {
                        for (int i = 0; i < query.getChild().getParameters().length; i++) {
                            childStmt.setString(i + 1, (String)row.get(query.getChild().getParameters()[i]));
                        }

                        ResultSet childSet = childStmt.executeQuery();
                        List<String> childColumns = prepareMetaData(childSet);
                        List<Map<String, ?>> childRows = new ArrayList<>();
                        while (childSet.next()) {
                            Map<String, Object> childRow = fillRow(childColumns, childSet);
                            childRows.add(childRow);
                        }

                        row.put("Zchild", childRows);
                        childSet.close();
                    }
                }

                rows.add(row);
            }

            debug ("Results loaded: " + rows.size());
            return rows;
        }
        catch (Exception e) {
            debug (e.getMessage());
        }

        return Collections.emptyList();
    }

    private String loadQueryDataCSV(String queryName) {
        // load XML query
        debug ("loading query from resource...");

        Query q = loadQuery(queryName);
        if (q == null) {
            debug ("Query with name: " + queryName + " not found");
            return "";
        }

        String query = q.getQuery();

        debug ("query: " + query);

        debug ("Ok. Trying to connect to " + url + "...");

        final StringBuilder sb = new StringBuilder();
        final char delimiter = ';';
        final char quotes = '"'; // Quotes symbol for strings

        try (Connection con = connect();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rSet = stmt.executeQuery()) {
            debug ("Statement executed.");

            ResultSetMetaData md = rSet.getMetaData();
            int len = md.getColumnCount();
            List<String> columns = new ArrayList<>(len);
            for (int i=1;i<=len;i++) {
                String colName = md.getColumnName(i);
                columns.add (colName);
                // Always in quotes
                sb.append(quotes).append(colName).append(quotes);
                sb.append(delimiter);
            }

            debug ("loaded columns: " + columns);
            int rows = 0;
            while (rSet.next()) {
                sb.append("\n"); // New line added

                for (int i=1;i<=len;i++) {
                    String colValue = rSet.getString(i);
                    switch (md.getColumnType(i)) {
                        case Types.DECIMAL:
                        case Types.NUMERIC:
                        case Types.DOUBLE:
                        case Types.BIGINT:
                        case Types.FLOAT:
                        case Types.INTEGER:
                        case Types.SMALLINT:
                            if (colValue != null) {
                                colValue = colValue.replaceAll("\\.", ",");
                            }
                            sb.append(colValue);
                            sb.append(delimiter);
                            break;

                        default:
                            if (colValue != null) {
                                colValue = colValue.replaceAll("НДС[^\\d]+(\\d+\\%).*", "$1");
                                colValue = colValue.replaceAll("(Без НДС).*", "$1");
                            }
                            sb.append(quotes).append(colValue).append(quotes);
                            sb.append(delimiter);
                    }
                }

                rows++;
            }

            debug ("Results loaded: " + rows);
        }
        catch (Exception e) {
            debug (e.getMessage());
        }

        return sb.toString();
    }

    static String toJson(Object obj) {
        debug ("Trying to serialize...");
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            debug(e.getMessage());
            return "";
        }
    }

    public static void main (String[] args) throws UnsupportedEncodingException {
        if (args.length < 6) {
            System.out.println("Usage: phrexp host port dbname username password queryName [fileName] [format]\n" +
                    "where host: hostname or ip address of database computer\n" +
                    "      port: TCP port of database listener\n" +
                    "      dbname: database name\n" +
                    "      username: login of database user\n" +
                    "      password: password for database user defined in previous parameter\n" +
                    "      queryName: name of query to execute\n" +
                    "      fileName: output file name\n" +
                    "      format: json or csv\n" +
                    "Example: phrexp localhost 1972 SAMPLE admin 123456");
        }
        else {
            String url = String.format("jdbc:Cache://%s:%s/%s", args[0], args[1], args[2]);
            AptekaPlus ap = new AptekaPlus(url, args[3], args[4]);

            // Store information
            if (args.length > 6) {
                byte[] result;

                String fileName = args[6];
                String format = args[7];

                switch (format.toLowerCase()) {
                    case "csv":
                        result = ap.loadQueryDataCSV (args[5]).getBytes("cp1251");
                        break;

                    case "json":
                    default:
                        result = toJson (ap.loadGoods(args[5])).getBytes("utf-8");
                        debug ("converted to JSON");
                }

                try (FileOutputStream fo = new FileOutputStream(fileName)) {
                    fo.write(result);
                    debug ("saved to file: " + fileName);
                }
                catch (Exception e) {
                    debug(e.getMessage());
                }
            }
            else {
                debug ("----------------- begin JSON output ---------------");
                System.out.println (toJson (ap.loadGoods(args[5])));
                debug ("----------------- end output ---------------");
            }
        }
    }
}
