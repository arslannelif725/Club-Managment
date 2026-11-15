package src.main.java;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.*;

public class ClubServer {
    // MySQL Bağlantı Bilgileri (Şifren yoksa password kısmını boş bırakabilirsin)
    private static final String DB_URL = "jdbc:mysql://localhost:3306/club_web_db?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Sayfa yönlendirmeleri
        server.createContext("/", new DashboardHandler());
        server.createContext("/add-member", new AddMemberHandler());

        server.setExecutor(null);
        server.start();
        System.out.println(">>> Sunucu baslatildi! Tarayicidan acin: http://localhost:" + port);
    }

    // Ana Sayfa ve Verileri Listeleme
    static class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>");
            html.append("<html lang='tr'><head><meta charset='UTF-8'><title>AGU Data Kulubu</title>");
            html.append("<style>body{font-family:Arial,sans-serif;margin:40px;background:#f4f7f6;color:#333;}");
            html.append("h1{color:#0056b3;} .card{background:#fff;padding:20px;margin-bottom:20px;border-radius:8px;box-shadow:0 2px 4px rgba(0,0,0,0.1);}");
            html.append("input, button{padding:10px;margin:5px 0;border:1px solid #ccc;border-radius:4px;}");
            html.append("button{background:#0056b3;color:#fff;cursor:pointer;} button:hover{background:#004085;}</style></head><body>");
            
            html.append("<h1>AGU Data Kulubu Yonetim Paneli</h1>");

            // Üye Ekleme Formu
            html.append("<div class='card'>");
            html.append("<h3>Yeni Uye Ekle</h3>");
            html.append("<form action='/add-member' method='POST'>");
            html.append("Ad Soyad: <input type='text' name='name' required><br>");
            html.append("Gorev/Rol: <input type='text' name='role' required><br>");
            html.append("<button type='submit'>Uye Kaydet</button>");
            html.append("</form></div>");

            // Üyeleri Listeleme Kartı
            html.append("<div class='card'>");
            html.append("<h3>Kayitli Uyeler</h3><ul>");
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM members")) {
                
                boolean anyMember = false;
                while (rs.next()) {
                    anyMember = true;
                    html.append("<li><strong>").append(rs.getString("name")).append("</strong> - ").append(rs.getString("role")).append("</li>");
                }
                if (!anyMember) {
                    html.append("<li>Henuz uye eklenmemis.</li>");
                }
            } catch (SQLException e) {
                html.append("<li>Veritabani baglanti hatasi: ").append(e.getMessage()).append("</li>");
            }
            
            html.append("</ul></div></body></html>");

            byte[] responseBytes = html.toString().getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }

    // Üye Ekleme İşlemi (Formdan gelen veriyi MySQL'e kaydeder)
    static class AddMemberHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String query = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String name = "";
                String role = "";

                // Form verilerini parse etme
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length > 1) {
                        if (pair[0].equals("name")) name = java.net.URLDecoder.decode(pair[1], "UTF-8");
                        if (pair[0].equals("role")) role = java.net.URLDecoder.decode(pair[1], "UTF-8");
                    }
                }

                // MySQL'e Kaydetme
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("INSERT INTO members (name, role) VALUES (?, ?)")) {
                    pstmt.setString(1, name);
                    pstmt.setString(2, role);
                    pstmt.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                // Ana sayfaya geri yönlendir
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(303, -1);
            }
        }
    }
}
