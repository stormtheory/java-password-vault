import java.security.SecureRandom;
import java.sql.*;

public class DatabaseUtilities 
{
    public static void registerShutdownHook(boolean isWindows) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Shutdown Hook] Running cleanup command...");

            try {
                // SECURITY CRITICAL: wipe password
                Backend.cleanupWipeDown();

                // Safe process execution
                ProcessBuilder pb = isWindows
                        ? new ProcessBuilder("cmd.exe", "/c", "echo Goodbye...")
                        : new ProcessBuilder("echo", "Goodbye...");

                pb.inheritIO();

                Process process = pb.start();
                int exitCode = process.waitFor();

                System.out.println(exitCode);

            } catch (Exception e) {
                System.err.println("[Shutdown Hook] Failed to run command: " + e.getMessage());
            }
        }, "shutdown-hook-thread"));
    }

    protected static String Pull_DB_Text_Meta_item(Connection conn, String key) throws Exception {
        String sql = "SELECT Tvalue FROM meta WHERE key = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Meta key not found: " + key);
                }
                return rs.getString(1);
            }
        }
    }

    protected static void Update_DB_Text_Meta_item(Connection conn, String key, String value) throws Exception {
        String sql = "UPDATE meta SET Tvalue = ? WHERE key = ?";
        try (PreparedStatement update = conn.prepareStatement(sql)) {
            update.setString(1, value);
            update.setString(2, key);
            int rows = update.executeUpdate();
            if (rows == 0) {
                throw new IllegalStateException("Update affected 0 rows — key not found: " + key);
            }
        }
    }
    // ===== DELETE ENTRY ===== ----- Yup
    protected void deleteEntry(Connection conn, int id) throws Exception {
        String sql = "DELETE FROM vault WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new IllegalStateException("Delete affected 0 rows — vault id not found: " + id);
            }
        }
    }

    // ===== User DELETE ===== ----- Yup
    protected void userdelEntry(Connection conn, String user_id) throws Exception {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user_id);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new IllegalStateException("Delete affected 0 rows — user_id not found: " + user_id);
            }
        }
    }

    public static char[] generatePassword(int length, boolean useABC, boolean use123, boolean useSpec) {
        // Build the character pool from enabled sets
        // Simple password generator
        String chars = "";
        if (useABC)  chars += "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        if (use123)  chars += "0123456789";
        if (useSpec) chars += "!@#$%^*()-_=+[]:.?";

        if (chars.isEmpty()) chars = "abcdefghijklmnopqrstuvwxyz";

        SecureRandom random = new SecureRandom();
        char[] password = new char[length];
        for (int i = 0; i < length; i++) {
            password[i] = chars.charAt(random.nextInt(chars.length()));
        }
        return password;
    }
}
