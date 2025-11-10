import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.json.JSONObject; // Assurez-vous d'avoir la dépendance org.json

@WebServlet("/createPaymentTest")
public class CreatePaymentServletTest extends HttpServlet {

    // --- VOS INFORMATIONS MONCASH (TEST) ---
    private static final String CLIENT_ID = "";
    private static final String CLIENT_SECRET = "JucuikGdv2-uwLx8KoTlDeSY2oT1ScsW54AWcb5sOmND-K9kHR1MGr_Jdb-Li36u";
    private static final String API_BASE_URL = "https://sandbox.moncashbutton.digicelgroup.com/Api";
    private static final String GATEWAY_BASE_URL = "https://sandbox.moncashbutton.digicelgroup.com/Moncash-middleware";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String orderId = req.getParameter("orderId");
        String amount = req.getParameter("amount");

        try {
            // 1. Obtenir le token d'authentification
            String authToken = getAuthToken();

            // 2. Créer le paiement
            URL url = new URL(API_BASE_URL + "/v1/CreatePayment");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            String jsonInputString = String.format("{\"amount\": %s, \"orderId\": \"%s\"}", amount, orderId);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 3. Analyser la réponse et rediriger vers la passerelle de paiement
            if (conn.getResponseCode() == 202) { // 202 Accepted
                String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JSONObject jsonResponse = new JSONObject(responseBody);
                String paymentToken = jsonResponse.getJSONObject("payment_token").getString("token");

                String redirectUrl = GATEWAY_BASE_URL + "/Payment/Redirect?token=" + paymentToken;
                resp.sendRedirect(redirectUrl);
            } else {
                // Gérer l'erreur
                String errorBody = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                resp.getWriter().write("Erreur lors de la création du paiement : " + errorBody);
            }

        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("Une erreur interne est survenue : " + e.getMessage());
        }
    }

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
