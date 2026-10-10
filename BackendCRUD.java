import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class BackendCRUD {
    // Database credentials for local Oracle XE setup
    static final String DB_URL = "jdbc:oracle:thin:@//localhost:1521/XE";
    static final String USER = "system";
    static final String PASS = "oracle";

    public static void main(String[] args) {
        try {
            // Ensure driver is loaded
            Class.forName("oracle.jdbc.driver.OracleDriver");
            
            // Check connection and create table if not exists
            try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                System.out.println("Connected to Oracle Database successfully!");
                createTableIfNotExists(conn);
            } catch (SQLException e) {
                System.err.println("Database Error during startup: " + e.getMessage());
            }

            // Start HTTP Server on port 8080
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/api/users", new ApiHandler());
            server.createContext("/api/query", new QueryHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Backend Server is running at http://localhost:8080");

        } catch (ClassNotFoundException e) {
            System.err.println("Driver Error: ojdbc6.jar not found on classpath.");
        } catch (IOException e) {
            System.err.println("Server Error: " + e.getMessage());
        }
    }

    private static void createTableIfNotExists(Connection conn) {
        String checkTable = "SELECT count(*) FROM user_tables WHERE table_name = 'USERS_CRUD'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkTable)) {
            if (rs.next() && rs.getInt(1) == 0) {
                String createTable = "CREATE TABLE USERS_CRUD (" +
                        "id NUMBER PRIMARY KEY, " +
                        "name VARCHAR2(100), " +
                        "email VARCHAR2(100))";
                stmt.execute(createTable);
                System.out.println("Table USERS_CRUD created successfully.");
            }
        } catch (SQLException e) {
            System.err.println("Table setup error: " + e.getMessage());
        }
    }

    static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            String response = "";
            int statusCode = 200;

            try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                if ("GET".equals(method)) {
                    response = handleGet(conn);
                } else if ("POST".equals(method)) {
                    handlePost(exchange, conn);
                    response = "{\"status\":\"success\"}";
                } else if ("PUT".equals(method)) {
                    handlePut(exchange, conn);
                    response = "{\"status\":\"success\"}";
                } else if ("DELETE".equals(method)) {
                    handleDelete(exchange, conn);
                    response = "{\"status\":\"success\"}";
                }
            } catch (SQLException e) {
                statusCode = 500;
                response = "{\"error\": \"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                e.printStackTrace();
            }

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }

        private String handleGet(Connection conn) throws SQLException {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, name, email FROM USERS_CRUD ORDER BY id")) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append(String.format("{\"id\":%d, \"name\":\"%s\", \"email\":\"%s\"}",
                            rs.getInt("id"),
                            escapeJson(rs.getString("name")),
                            escapeJson(rs.getString("email"))));
                    first = false;
                }
            }
            sb.append("]");
            return sb.toString();
        }

        private void handlePost(HttpExchange exchange, Connection conn) throws IOException, SQLException {
            Map<String, String> params = parseBody(exchange);
            String name = params.get("name");
            String email = params.get("email");

            String sql = "INSERT INTO USERS_CRUD (id, name, email) VALUES ((SELECT COALESCE(MAX(id), 0) + 1 FROM USERS_CRUD), ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, email);
                pstmt.executeUpdate();
            }
        }

        private void handlePut(HttpExchange exchange, Connection conn) throws IOException, SQLException {
            Map<String, String> params = parseBody(exchange);
            int id = Integer.parseInt(params.get("id"));
            String name = params.get("name");
            String email = params.get("email");

            String sql = "UPDATE USERS_CRUD SET name = ?, email = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, name);
                pstmt.setString(2, email);
                pstmt.setInt(3, id);
                pstmt.executeUpdate();
            }
        }

        private void handleDelete(HttpExchange exchange, Connection conn) throws IOException, SQLException {
            Map<String, String> params = parseBody(exchange);
            int id = Integer.parseInt(params.get("id"));

            String sql = "DELETE FROM USERS_CRUD WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.executeUpdate();
            }
        }

        private Map<String, String> parseBody(HttpExchange exchange) throws IOException {
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> map = new HashMap<>();
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
            return map;
        }

        private String escapeJson(String data) {
            if (data == null) return "";
            return data.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    static class QueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String response = "";
            int statusCode = 200;

            try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                if ("POST".equals(exchange.getRequestMethod())) {
                    InputStream is = exchange.getRequestBody();
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    
                    String query = "";
                    String[] pairs = body.split("&");
                    for (String pair : pairs) {
                        String[] kv = pair.split("=");
                        if (kv.length == 2 && "query".equals(kv[0])) {
                            query = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        }
                    }

                    if (query != null && !query.trim().isEmpty()) {
                        try (Statement stmt = conn.createStatement()) {
                            boolean isResultSet = stmt.execute(query);
                            if (isResultSet) {
                                try (ResultSet rs = stmt.getResultSet()) {
                                    ResultSetMetaData rsmd = rs.getMetaData();
                                    int colCount = rsmd.getColumnCount();
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("[");
                                    boolean firstRow = true;
                                    while (rs.next()) {
                                        if (!firstRow) sb.append(",");
                                        sb.append("{");
                                        for (int i = 1; i <= colCount; i++) {
                                            if (i > 1) sb.append(",");
                                            sb.append("\"").append(rsmd.getColumnName(i)).append("\":\"");
                                            String val = rs.getString(i);
                                            sb.append(val != null ? val.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") : "");
                                            sb.append("\"");
                                        }
                                        sb.append("}");
                                        firstRow = false;
                                    }
                                    sb.append("]");
                                    response = "{\"status\":\"success\", \"data\":" + sb.toString() + "}";
                                }
                            } else {
                                int updateCount = stmt.getUpdateCount();
                                response = "{\"status\":\"success\", \"message\":\"Query executed successfully. Rows affected: " + updateCount + "\"}";
                            }
                        }
                    } else {
                        statusCode = 400;
                        response = "{\"error\": \"Empty query\"}";
                    }
                }
            } catch (SQLException e) {
                statusCode = 500;
                response = "{\"error\": \"" + e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
                e.printStackTrace();
            }

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
