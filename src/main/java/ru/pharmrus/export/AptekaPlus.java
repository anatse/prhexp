package ru.pharmrus.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import com.intersys.jdbc.*;

public class AptekaPlus {
    private final String url;
    private final String driverClass = "com.intersys.jdbc.CacheDriver";
    private final String userName;
    private final String password;

    private static final SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.YYYY HH:mm");

    public AptekaPlus(String url, String userName, String password) {
        this.url = url;
        this.userName = userName;
        this.password = password;
    }

    protected Connection connect() throws ClassNotFoundException, SQLException {
        CacheDataSource ds = new CacheDataSource();
        ds.setURL(url);
        ds.setUser(userName);
        ds.setPassword(password);
        Connection con = ds.getConnection();
        debug ("Connected.");
        return con;
    }

    protected static void debug (String message) {
        Date dt = new Date();
        String sDate = fmt.format(dt);
        System.out.printf("%s: %s\n", sDate, message);
    }

    public static String loadQuery (String queryName) {
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

            return getCharacterDataFromElement (element);
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

    public List<Map<String, String>> loadGoods (String queryName) {
        // load XML query
        debug ("loading query from resource...");

        String query = loadQuery(queryName);

        debug ("Ok. Trying to connect to " + url + "...");

        try (Connection con = connect();
             PreparedStatement stmt = con.prepareStatement(query);
             ResultSet rSet = stmt.executeQuery()) {
            debug ("Statement executed.");

            ResultSetMetaData md = rSet.getMetaData();
            int len = md.getColumnCount();
            List<String> columns = new ArrayList<>(len);
            for (int i=1;i<=len;i++) {
                columns.add (md.getColumnName(i));
            }

            debug ("loaded columns: " + columns);

            List<Map<String, String>> rows = new ArrayList<>();
            while (rSet.next()) {
                Map<String, String> row = new HashMap<>();
                for (int i=1;i<=len;i++) {
                    row.put(columns.get(i-1), rSet.getString(i));
                }

                rows.add(row);
            }

            debug ("Results loaded: " + rows.size());
            return rows;
        }
        catch (SQLException e) {
            debug (e.getMessage());
        }
        catch (ClassNotFoundException e) {
            debug (e.getMessage());
        }

        return Collections.emptyList();
    }

    public String loadQueryDataCSV (String queryName) {
        // load XML query
        debug ("loading query from resource...");

        String query = loadQuery(queryName);

        debug ("query: " + query);

        debug ("Ok. Trying to connect to " + url + "...");

        final StringBuffer sb = new StringBuffer();
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
                            sb.append(colValue);
                            sb.append(delimiter);
                            break;

                        default:
                            sb.append(quotes).append(colValue).append(quotes);
                            sb.append(delimiter);
                    }
                }

                rows++;
            }

            debug ("Results loaded: " + rows);
        }
        catch (SQLException e) {
            debug (e.getMessage());
        }
        catch (ClassNotFoundException e) {
            debug (e.getMessage());
        }

        return sb.toString();
    }

    public static String toJson (Object obj) {
        debug ("Trying to serialize...");
        ObjectMapper mapper = new ObjectMapper();
        //mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            debug(e.getMessage());
            return null;
        }
    }

    public static void main (String[] args) {
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
                String result;

                String fileName = args[6];
                String format = args.length > 6 ? args[7] : "json";

                switch (format.toLowerCase()) {
                    case "csv":
                        result = ap.loadQueryDataCSV (args[5]);
                        break;

                    case "json":
                    default:
                        result = toJson (ap.loadGoods(args[5]));
                        debug ("converted to JSON");
                }

                try (FileOutputStream fo = new FileOutputStream(fileName)) {
                    fo.write(result.getBytes("cp1251"));
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
