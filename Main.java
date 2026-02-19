import java.net.HttpURLConnection;
import java.net.URL;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.util.ArrayList;
import java.util.Scanner;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.BindException;

@SuppressWarnings("deprecation")
public class Main {

    // Product class
    static class Product {
        int id;
        String name;
        double price;

        Product(int id, String name, double price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }
    }

    static ArrayList<Product> products = new ArrayList<>();
    static ArrayList<Product> cart = new ArrayList<>();
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {

        fetchProductsFromAPI();
        startHttpServer();

        while (true) {
            System.out.println("\n===== E-COMMERCE PRODUCT APP =====");
            System.out.println("1. View Products");
            System.out.println("2. View Cart");
            System.out.println("3. Exit");
            System.out.print("Enter choice: ");

            int choice = sc.nextInt();

            switch (choice) {
                case 1:
                    showProducts();
                    break;
                case 2:
                    showCart();
                    break;
                case 3:
                    System.out.println("Thank you for shopping!");
                    return;
                default:
                    System.out.println("Invalid choice!");
            }
        }
    }

    // Start a tiny HTTP server exposing /products
    static void startHttpServer() {
        HttpServer server = null;
        int boundPort = -1;
        try {
            server = HttpServer.create(new InetSocketAddress(8000), 0);
            boundPort = 8000;
        } catch (BindException be) {
            try {
                // try ephemeral port
                server = HttpServer.create(new InetSocketAddress(0), 0);
                boundPort = server.getAddress().getPort();
            } catch (Exception e) {
                System.out.println("Failed to start HTTP server: " + e.getMessage());
                return;
            }
        } catch (Exception e) {
            System.out.println("Failed to start HTTP server: " + e.getMessage());
            return;
        }

        server.createContext("/products", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    String method = exchange.getRequestMethod();
                    if (!"GET".equalsIgnoreCase(method)) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }

                    String json = productsToJson();
                    byte[] resp = json.getBytes("UTF-8");
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, resp.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(resp);
                    os.close();
                } catch (Exception e) {
                    try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) {}
                }
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.println("HTTP server started on http://localhost:" + boundPort + "/products");
    }

    static String productsToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append("{");
            sb.append("\"id\":").append(p.id).append(",");
            sb.append("\"name\":\"").append(escapeJson(p.name)).append("\",");
            sb.append("\"price\":").append(p.price);
            sb.append("}");
            if (i < products.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Fetch products from FREE API
    static void fetchProductsFromAPI() {
        try {
            URL url = new URL("https://fakestoreapi.com/products");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);

            int status = con.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP response code " + status);
            }

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream())
            );

            StringBuilder json = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                json.append(line);
            }
            br.close();

            parseProducts(json.toString());
            System.out.println("Fetched " + products.size() + " products from remote API.");

        } catch (Exception e) {
            System.out.println("Failed fetching products from API: " + e.getMessage());
            // Fallback data if API fails
            products.add(new Product(1, "Laptop", 55000));
            products.add(new Product(2, "Mobile", 25000));
            products.add(new Product(3, "Headphones", 2000));
        }
    }

    // Simple JSON parsing (no external library)
    static void parseProducts(String json) {
        String[] items = json.split("\\},\\{");
        int id = 1;

        for (String item : items) {
            String title = extract(item, "\"title\":\"", "\"");
            String priceStr = extractPrice(item, "\"price\":");

            if (!title.isEmpty() && !priceStr.isEmpty()) {
                try {
                    double price = Double.parseDouble(priceStr);
                    products.add(new Product(id++, title, price));
                } catch (NumberFormatException nfe) {
                    // skip malformed price
                }
            }
        }
    }

    static String extract(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s == -1) return "";
        s += start.length();
        int e = text.indexOf(end, s);
        if (e == -1) return text.substring(s);
        return text.substring(s, e);
    }

    static String extractPrice(String text, String start) {
        int s = text.indexOf(start);
        if (s == -1) return "";
        s += start.length();
        int eComma = text.indexOf(',', s);
        int eBrace = text.indexOf('}', s);
        int eBracket = text.indexOf(']', s);
        int e = -1;
        if (eComma != -1) e = eComma;
        if (eBrace != -1 && (e == -1 || eBrace < e)) e = eBrace;
        if (eBracket != -1 && (e == -1 || eBracket < e)) e = eBracket;
        if (e == -1) return text.substring(s).trim();
        return text.substring(s, e).trim();
    }

    static void showProducts() {
        System.out.println("\n--- Product List ---");
        for (Product p : products) {
            System.out.println(p.id + ". " + p.name + " - Rs. " + p.price);
        }

        System.out.print("Enter product ID to add to cart (0 to back): ");
        int id = sc.nextInt();

        if (id == 0) return;

        for (Product p : products) {
            if (p.id == id) {
                cart.add(p);
                System.out.println("Product added to cart!");
                return;
            }
        }
        System.out.println("Product not found!");
    }

    static void showCart() {
        if (cart.isEmpty()) {
            System.out.println("\nCart is empty!");
            return;
        }

        double total = 0;
        System.out.println("\n--- Cart Summary ---");

        for (int i = 0; i < cart.size(); i++) {
            Product p = cart.get(i);
            System.out.println((i + 1) + ". " + p.name + " - Rs. " + p.price);
            total += p.price;
        }

        System.out.println("Total Items: " + cart.size());
        System.out.println("Total Price: Rs. " + total);

        System.out.print("Enter item number to remove (0 to back): ");
        int r = sc.nextInt();

        if (r > 0 && r <= cart.size()) {
            cart.remove(r - 1);
            System.out.println("Item removed!");
        }
    }
}
