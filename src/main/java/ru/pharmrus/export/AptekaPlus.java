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

    public List<Map<String, String>> loadGoods () {
        // load XML query
        debug ("loading query from resource...");

        String query = loadQuery("Goods");

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
        if (args.length < 5) {
            System.out.println("Usage: phrexp host port dbname username password\n" +
                    "where host: hostname or ip address of database computer\n" +
                    "      port: TCP port of database listener\n" +
                    "      dbname: database name\n" +
                    "      username: login of database user\n" +
                    "      password: password for database user defined in previous parameter\n" +
                    "Example: phrexp localhost 1972 SAMPLE admin 123456");
        }
        else {
            String url = String.format("jdbc:Cache://%s:%s/%s", args[0], args[1], args[2]);
            AptekaPlus ap = new AptekaPlus(url, args[3], args[4]);
            List<Map<String, String>> goods = ap.loadGoods();
            // Store goods in json format
            String result = toJson (goods);
            debug ("converted to JSON");
            if (args.length > 5) {
                String fileName = args[5];
                try (FileOutputStream fo = new FileOutputStream(fileName)) {
                    fo.write(result.getBytes("utf-8"));
                    debug ("saved to file: " + fileName);
                }
                catch (Exception e) {
                    debug(e.getMessage());
                }
            }
        }
    }
}
