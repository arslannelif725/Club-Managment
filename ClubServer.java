package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class ClubServer {
    private static final String DB_URL = "jdbc:mysql://mysql-f9f048c-arslannelif725-19f6.g.aivencloud.com:15014/defaultdb?sslMode=REQUIRED";
    private static final String DB_USER = "avnadmin";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    private static String loggedInUser = null;
    private static String loggedInRole = null;

    public static void main(String[] args) throws IOException {
        initDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new HomeHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/logout", new LogoutHandler());
        server.createContext("/add-member", new AddMemberHandler());
        server.createContext("/edit-member", new EditMemberHandler());
        server.createContext("/delete-member", new DeleteMemberHandler());
        server.createContext("/add-task", new AddTaskHandler());
        server.createContext("/complete-task", new CompleteTaskHandler());
        server.createContext("/add-group", new AddGroupHandler());
        server.createContext("/delete-group", new DeleteGroupHandler());
        server.createContext("/add-announcement", new AddAnnouncementHandler());
        server.createContext("/delete-announcement", new DeleteAnnouncementHandler());

        server.setExecutor(null);
        server.start();
        System.out.println(">>> Sunucu baslatildi! Tarayicidan acin: http://localhost:8080");
    }

    private static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS members (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, role VARCHAR(100) DEFAULT 'Üye')");
            stmt.execute("CREATE TABLE IF NOT EXISTS tasks (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255) NOT NULL, description TEXT, assigned_to VARCHAR(100), status VARCHAR(50) DEFAULT 'Bekliyor')");
            stmt.execute("CREATE TABLE IF NOT EXISTS club_groups (id INT AUTO_INCREMENT PRIMARY KEY, group_name VARCHAR(100) NOT NULL, target_role VARCHAR(100) NOT NULL, group_members VARCHAR(255))");
            stmt.execute("CREATE TABLE IF NOT EXISTS announcements (id INT AUTO_INCREMENT PRIMARY KEY, message TEXT NOT NULL, author VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> parseFormData(String requestBody) {
        Map<String, String> map = new HashMap<>();
        if (requestBody == null || requestBody.isEmpty()) return map;
        String[] pairs = requestBody.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length > 1) {
                map.put(
                        URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(keyValue[1].replace("+", "%20"), StandardCharsets.UTF_8)
                );
            } else if (keyValue.length == 1) {
                map.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>AGÜ Data Club</title>");
            html.append("<style>body{font-family:Arial;background:#f4f7f6;margin:0;padding:20px;color:#333;}");
            html.append(".container{max-width:900px;margin:auto;background:white;padding:20px;border-radius:8px;box-shadow:0 2px 5px rgba(0,0,0,0.1);}");
            html.append("h2{color:#0056b3;border-bottom:2px solid #eee;padding-bottom:5px;}");
            html.append("table{width:100%;border-collapse:collapse;margin-top:10px;}");
            html.append("th,td{padding:10px;border:1px solid #ddd;text-align:left;}th{background:#0056b3;color:white;}");
            html.append("input,select,textarea{width:100%;padding:8px;margin:5px 0 15px 0;border:1px solid #ccc;border-radius:4px;box-sizing:border-box;}");
            html.append("button{background:#28a745;color:white;padding:10px 15px;border:none;border-radius:4px;cursor:pointer;}");
            html.append("button:hover{background:#218838;}.btn-danger{background:#dc3545;}.btn-danger:hover{background:#c82333;}");
            html.append(".badge-success{background:#28a745;color:white;padding:4px 8px;border-radius:4px;font-size:12px;}");
            html.append(".badge-waiting{background:#ffc107;color:#333;padding:4px 8px;border-radius:4px;font-size:12px;}");
            html.append(".section{margin-bottom:30px;}.user-box{background:#e2e3e5;padding:10px;border-radius:5px;margin-bottom:20px;display:flex;justify-content:space-between;align-items:center;}</style></head><body>");

            html.append("<div class='container'>");

            if (loggedInUser == null) {
                html.append("<h1>AGÜ Data Club - Üye Girişi</h1>");
                html.append("<form action='/login' method='POST' accept-charset='UTF-8'>");
                html.append("Ad Soyad: <input type='text' name='name' placeholder='Adınızı girin' required>");
                html.append("Giriş Rolü / Yetkisi: <select name='role'><option value='Üye'>Normal Üye</option><option value='Koordinatör'>Yönetici / Koordinatör</option></select>");
                html.append("<button type='submit'>Giriş Yap</button></form>");
                html.append("</div></body></html>");
            } else {
                html.append("<div class='user-box'><span>Hoş geldin, <b>").append(loggedInUser).append("</b> (Rol: <i>").append(loggedInRole).append("</i>)</span>");
                html.append("<form action='/logout' method='POST' style='margin:0;'><button type='submit' class='btn-danger'>Çıkış Yap</button></form></div>");

                html.append("<h1>AGÜ Data Club Yönetim Paneli</h1>");

                boolean isAdmin = loggedInRole.equalsIgnoreCase("Koordinatör") || loggedInRole.equalsIgnoreCase("İletişim Başkanı") || loggedInRole.equalsIgnoreCase("Yönetici");

                if (isAdmin) {
                    html.append("<div class='section'><h2>Yeni Üye Ekle</h2>");
                    html.append("<form action='/add-member' method='POST' accept-charset='UTF-8'>");
                    html.append("Ad Soyad: <input type='text' name='name' required>");
                    html.append("Rol: <input type='text' name='role' placeholder='Örn: Yazılım Ekip Lideri' required>");
                    html.append("<button type='submit'>Üye Ekle</button></form></div>");

                    html.append("<div class='section'><h2>Kulüp Üyeleri</h2><table><tr><th>ID</th><th>Ad Soyad</th><th>Rol</th><th>İşlemler</th></tr>");
                    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                         Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT * FROM members")) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            String name = rs.getString("name");
                            String role = rs.getString("role");
                            html.append("<tr><td>").append(id).append("</td><td>").append(name).append("</td><td>").append(role).append("</td>");
                            html.append("<td><form action='/edit-member' method='POST' accept-charset='UTF-8' style='display:inline;'>");
                            html.append("<input type='hidden' name='id' value='").append(id).append("'>");
                            html.append("Rol: <input type='text' name='role' value='").append(role).append("' style='width:120px;display:inline;'> ");
                            html.append("<button type='submit'>Güncelle</button></form> ");
                            html.append("<form action='/delete-member' method='POST' style='display:inline;'>");
                            html.append("<input type='hidden' name='id' value='").append(id).append("'>");
                            html.append("<button type='submit' class='btn-danger'>Sil</button></form></td></tr>");
                        }
                    } catch (SQLException e) { e.printStackTrace(); }
                    html.append("</table></div>");
                }

                // Duyurular Bölümü
                html.append("<div class='section'><h2>Ortak Duyurular</h2>");
                if (isAdmin) {
                    html.append("<form action='/add-announcement' method='POST' accept-charset='UTF-8'>");
                    html.append("Duyuru Mesajı: <textarea name='message' required></textarea>");
                    html.append("<button type='submit'>Duyuru Yayınla</button></form>");
                }
                html.append("<ul>");
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM announcements ORDER BY created_at DESC")) {
                    while (rs.next()) {
                        int annId = rs.getInt("id");
                        String msg = rs.getString("message");
                        String author = rs.getString("author");
                        String date = rs.getString("created_at");
                        html.append("<li><b>[").append(date != null ? date : "").append("] ").append(author != null ? author : "Yönetim").append(":</b> ").append(msg);
                        if (isAdmin) {
                            html.append(" <form action='/delete-announcement' method='POST' style='display:inline;'><input type='hidden' name='id' value='").append(annId).append("'><button type='submit' class='btn-danger' style='padding:2px 6px;font-size:11px;'>Sil</button></form>");
                        }
                        html.append("</li>");
                    }
                } catch (SQLException e) { e.printStackTrace(); }
                html.append("</ul></div>");

                // Görevler Bölümü
                html.append("<div class='section'><h2>Görevler</h2>");
                if (isAdmin) {
                    html.append("<form action='/add-task' method='POST' accept-charset='UTF-8'>");
                    html.append("Görev Başlığı: <input type='text' name='title' required>");
                    html.append("Açıklama: <textarea name='description'></textarea>");
                    html.append("Atanan Kişi veya Rol: <input type='text' name='assigned_to' placeholder='Örn: y, Ahmet, Zeynep veya Üye' required>");
                    html.append("<button type='submit'>Görev Ata</button></form>");
                }
                html.append("<h3>Mevcut Görevler</h3><table><tr><th>Başlık</th><th>Açıklama</th><th>Atanan</th><th>Durum</th><th>İşlem</th></tr>");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    PreparedStatement pstmt;
                    if (isAdmin) {
                        pstmt = conn.prepareStatement("SELECT * FROM tasks");
                    } else {
                        // Virgüle ayrılmış listede ad veya rol geçiyorsa görevi getir
                        pstmt = conn.prepareStatement("SELECT * FROM tasks WHERE assigned_to LIKE ? OR assigned_to LIKE ? OR assigned_to = 'Üye'");
                        pstmt.setString(1, "%" + loggedInUser + "%");
                        pstmt.setString(2, "%" + loggedInRole + "%");
                    }
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        int taskId = rs.getInt("id");
                        String title = rs.getString("title");
                        String desc = rs.getString("description");
                        String assigned = rs.getString("assigned_to");
                        String status = rs.getString("status");

                        html.append("<tr><td>").append(title).append("</td><td>").append(desc).append("</td><td>").append(assigned).append("</td>");

                        if ("Başarılı".equalsIgnoreCase(status)) {
                            html.append("<td><span class='badge-success'>Başarılı</span></td><td>-</td>");
                        } else {
                            html.append("<td><span class='badge-waiting'>Bekliyor</span></td>");
                            html.append("<td><form action='/complete-task' method='POST' style='margin:0;'>");
                            html.append("<input type='hidden' name='id' value='").append(taskId).append("'>");
                            html.append("<button type='submit' style='padding:5px 10px;font-size:12px;'>✔ Tamamla</button></form></td>");
                        }
                        html.append("</tr>");
                    }
                } catch (SQLException e) { e.printStackTrace(); }
                html.append("</table></div>");

                // Gruplar Bölümü
                html.append("<div class='section'><h2>Kulüp Grupları</h2>");
                if (isAdmin) {
                    html.append("<form action='/add-group' method='POST' accept-charset='UTF-8'>");
                    html.append("Grup Adı: <input type='text' name='group_name' required>");
                    html.append("Hedef Rol / Alan: <input type='text' name='target_role' placeholder='Örn: Yazılım Ekibi' required>");
                    html.append("Grup Üyeleri: <input type='text' name='group_members' placeholder='Örn: y, Ahmet, Zeynep' required>");
                    html.append("<button type='submit'>Grup Oluştur & Üye Ekle</button></form>");
                }
                html.append("<h3>Gruplarım</h3><table><tr><th>Grup Adı</th><th>Hedef Rol</th><th>Grup Üyeleri</th>");
                if (isAdmin) html.append("<th>İşlem</th>");
                html.append("</tr>");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    PreparedStatement pstmt;
                    if (isAdmin) {
                        pstmt = conn.prepareStatement("SELECT * FROM club_groups");
                    } else {
                        pstmt = conn.prepareStatement("SELECT * FROM club_groups WHERE target_role LIKE ? OR group_members LIKE ? OR target_role = 'Üye'");
                        pstmt.setString(1, "%" + loggedInRole + "%");
                        pstmt.setString(2, "%" + loggedInUser + "%");
                    }
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        int gId = rs.getInt("id");
                        String gName = rs.getString("group_name");
                        String tRole = rs.getString("target_role");
                        String gMembers = rs.getString("group_members");

                        html.append("<tr><td>").append(gName).append("</td><td>").append(tRole).append("</td><td>").append(gMembers != null && !gMembers.isEmpty() ? gMembers : "Üye bulunmuyor").append("</td>");
                        if (isAdmin) {
                            html.append("<td><form action='/delete-group' method='POST'><input type='hidden' name='id' value='").append(gId).append("'><button type='submit' class='btn-danger'>Sil</button></form></td>");
                        }
                        html.append("</tr>");
                    }
                } catch (SQLException e) { e.printStackTrace(); }
                html.append("</table></div>");

                html.append("</div>");
            }

            html.append("</body></html>");

            byte[] responseBytes = html.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }

    static class LoginHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> data = parseFormData(body);
                String name = data.get("name");
                String selectedRole = data.get("role");

                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM members WHERE name = ?")) {
                    pstmt.setString(1, name);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        loggedInUser = rs.getString("name");
                        loggedInRole = selectedRole.equals("Koordinatör") ? "Koordinatör" : rs.getString("role");
                    } else {
                        try (PreparedStatement insertPstmt = conn.prepareStatement("INSERT INTO members (name, role) VALUES (?, ?)")) {
                            insertPstmt.setString(1, name);
                            insertPstmt.setString(2, selectedRole);
                            insertPstmt.executeUpdate();
                        }
                        loggedInUser = name;
                        loggedInRole = selectedRole;
                    }
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class LogoutHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            loggedInUser = null;
            loggedInRole = null;
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class AddMemberHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("INSERT INTO members (name, role) VALUES (?, ?)")) {
                    pstmt.setString(1, data.get("name"));
                    pstmt.setString(2, data.get("role"));
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class EditMemberHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("UPDATE members SET role = ? WHERE id = ?")) {
                    pstmt.setString(1, data.get("role"));
                    pstmt.setInt(2, Integer.parseInt(data.get("id")));
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class DeleteMemberHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("DELETE FROM members WHERE id = ?")) {
                    pstmt.setInt(1, Integer.parseInt(data.get("id")));
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class AddTaskHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("INSERT INTO tasks (title, description, assigned_to, status) VALUES (?, ?, ?, 'Bekliyor')")) {
                    pstmt.setString(1, data.get("title"));
                    pstmt.setString(2, data.get("description"));
                    pstmt.setString(3, data.get("assigned_to"));
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class CompleteTaskHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("UPDATE tasks SET status = 'Başarılı' WHERE id = ?")) {
                    pstmt.setInt(1, Integer.parseInt(data.get("id")));
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class AddGroupHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("INSERT INTO club_groups (group_name, target_role, group_members) VALUES (?, ?, ?)")) {
                    pstmt.setString(1, data.get("group_name"));
                    pstmt.setString(2, data.get("target_role"));
                    pstmt.setString(3, data.get("group_members"));
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class DeleteGroupHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("DELETE FROM club_groups WHERE id = ?")) {
                    pstmt.setInt(1, Integer.parseInt(data.get("id")));
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class AddAnnouncementHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("INSERT INTO announcements (message, author) VALUES (?, ?)")) {
                    pstmt.setString(1, data.get("message"));
                    pstmt.setString(2, loggedInUser);
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }

    static class DeleteAnnouncementHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> data = parseFormData(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("DELETE FROM announcements WHERE id = ?")) {
                    pstmt.setInt(1, Integer.parseInt(data.get("id")));
                    pstmt.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(303, -1);
        }
    }
}
