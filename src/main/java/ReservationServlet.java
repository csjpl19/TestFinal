import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/reservationServlet")
public class ReservationServlet extends HttpServlet {

    // --- CONFIGUREZ VOTRE CONNEXION À LA BASE DE DONNÉES ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/TestReservation";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Monmdpsql";

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");

        // Récupérer les données du formulaire
        String fullname = req.getParameter("fullname");
        String email = req.getParameter("email");
        String sexe = req.getParameter("sexe");
        String checkinStr = req.getParameter("checkin");
        String checkoutStr = req.getParameter("checkout");

        Connection conn = null;
        String redirectUrl = "reservation.html";
        String message = "";
        String status = "error";

        try {
            // Charger le driver JDBC
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            conn.setAutoCommit(false); // Activer les transactions

            // 1. Insérer le client et récupérer l'ID généré
            String sqlClient = "INSERT INTO clients (fullname, email, sexe) VALUES (?, ?, ?)";
            long clientId = 0;
            try (PreparedStatement stmtClient = conn.prepareStatement(sqlClient, Statement.RETURN_GENERATED_KEYS)) {
                stmtClient.setString(1, fullname);
                stmtClient.setString(2, email);
                stmtClient.setString(3, sexe);
                stmtClient.executeUpdate();

                try (ResultSet generatedKeys = stmtClient.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        clientId = generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("La création du client a échoué, aucun ID obtenu.");
                    }
                }
            }

            // 2. Insérer la réservation avec l'ID du client
            String sqlReservation = "INSERT INTO reservations (client_id, checkin, checkout, statut) VALUES (?, ?, ?, ?)";
            long reservationId = 0;
             try (PreparedStatement stmtReservation = conn.prepareStatement(sqlReservation, Statement.RETURN_GENERATED_KEYS)) {
                stmtReservation.setLong(1, clientId);
                stmtReservation.setDate(2, java.sql.Date.valueOf(checkinStr));
                stmtReservation.setDate(3, java.sql.Date.valueOf(checkoutStr));
                stmtReservation.setString(4, "Non-confirmee"); // Statut initial
                stmtReservation.executeUpdate();
                
                try (ResultSet generatedKeys = stmtReservation.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        reservationId = generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("La création de la réservation a échoué, aucun ID obtenu.");
                    }
                }
            }

            conn.commit(); // Valider la transaction

            // 3. Calculer le montant
            LocalDate checkin = LocalDate.parse(checkinStr);
            LocalDate checkout = LocalDate.parse(checkoutStr);
            long days = ChronoUnit.DAYS.between(checkin, checkout);
            long amount = days * 100;

            // Préparer la redirection avec les informations nécessaires pour le paiement
            status = "success";
            message = "Réservation enregistrée avec succès ! Vous pouvez maintenant procéder au paiement.";
            redirectUrl = String.format("index.html?status=success&orderId=%d&amount=%d&message=%s",
                reservationId, amount, URLEncoder.encode(message, StandardCharsets.UTF_8.name()));

        } catch (ClassNotFoundException | SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback(); // Annuler la transaction en cas d'erreur
                } catch (SQLException ex) {
                    ex.printStackTrace(); // Log l'erreur de rollback
                }
            }
            message = "Erreur lors de l'enregistrement : " + e.getMessage();
            e.printStackTrace(); // Log l'erreur principale
            redirectUrl = String.format("index.html?status=error&message=%s", 
                URLEncoder.encode(message, StandardCharsets.UTF_8.name()));

        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            resp.sendRedirect(redirectUrl);
        }
    }
}