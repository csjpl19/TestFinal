import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.json.JSONObject;

@WebServlet("/callbackTest")
public class CallbackServletTest extends HttpServlet {

    // --- VOS INFORMATIONS ---
    private static final String CLIENT_ID = "";
    private static final String CLIENT_SECRET = "JucuikGdv2-uwLx8KoTlDeSY2oT1ScsW54AWcb5sOmND-K9kHR1MGr_Jdb-Li36u";
    private static final String API_BASE_URL = "https://sandbox.moncashbutton.digicelgroup.com/Api";
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/TestReservation";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Monmdpsql";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // MonCash peut renvoyer l'ID de transaction via un GET
        String transactionId = req.getParameter("transactionId");
        String message = "";
        String status = "error";

        if (transactionId == null || transactionId.isEmpty()) {
            message = "Aucun ID de transaction reçu.";
        } else {
            try {
                // 1. Obtenir le token d'authentification
                String authToken = getAuthToken();

                // 2. Vérifier les détails de la transaction
                URL url = new URL(API_BASE_URL + "/v1/RetrieveTransactionPayment");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInputString = String.format("{\"transactionId\": \"%s\"}", transactionId);
                try (OutputStream os = conn.getOutputStream()){
                    os.write(jsonInputString.getBytes(StandardCharsets.UTF_8));
                }

                if (conn.getResponseCode() == 200) {
                    String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    JSONObject payment = jsonResponse.getJSONObject("payment");
                    String paymentMessage = payment.getString("message");
                    String orderId = payment.getString("reference"); // MonCash utilise "reference" pour l'orderId

                    if ("successful".equalsIgnoreCase(paymentMessage)) {
                        // 3. Mettre à jour la base de données
                        updateDatabase(orderId, transactionId, payment.getDouble("cost"));
                        status = "success";
                        message = "Paiement réussi ! Votre réservation est confirmée.";
                    } else {
                        message = "Le paiement a échoué. Statut : " + paymentMessage;
                    }
                } else {
                    message = "Impossible de vérifier la transaction auprès de MonCash.";
                }

            } catch (Exception e) {
                e.printStackTrace();
                message = "Erreur interne lors de la vérification du paiement : " + e.getMessage();
            }
        }
        
        // Rediriger vers la page de réservation avec un message de statut
        String redirectUrl = String.format("reservation.html?status=%s&message=%s", 
            status, URLEncoder.encode(message, StandardCharsets.UTF_8.name()));
        resp.sendRedirect(redirectUrl);
    }

    private void updateDatabase(String orderId, String transactionId, double amount) throws SQLException, ClassNotFoundException {
        Connection conn = null;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false);

            // Mettre à jour le statut de la réservation
            String updateReservationSql = "UPDATE reservations SET statut = 'Confirmee' WHERE id_reservation = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateReservationSql)) {
                stmt.setLong(1, Long.parseLong(orderId));
                stmt.executeUpdate();
            }

            // Insérer dans la table des transactions
            String insertTransactionSql = "INSERT INTO transactions (id_transaction_moncash, id_reservation, montant, statut_paiement) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertTransactionSql)) {
                stmt.setString(1, transactionId);
                stmt.setLong(2, Long.parseLong(orderId));
                stmt.setDouble(3, amount);
                stmt.setString(4, "successful");
                stmt.executeUpdate();
            }

            conn.commit();
        } catch (SQLException | ClassNotFoundException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) conn.close();
        }
    }
    
    // La méthode getAuthToken() est la même que dans CreatePaymentServlet
    private String getAuthToken() throws IOException {
        URL url = new URL(API_BASE_URL + "/oauth/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + encodedCredentials);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        String postData = "scope=read,write&grant_type=client_credentials";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData.getBytes(StandardCharsets.UTF_8));
        }
        if (conn.getResponseCode() == 200) {
            String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonResponse = new JSONObject(responseBody);
            return jsonResponse.getString("access_token");
        } else {
            throw new IOException("Échec de l'obtention du token d'authentification. Code: " + conn.getResponseCode());
        }
    }
}
