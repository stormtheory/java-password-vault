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

    protected String Pull_DB_Status(Connection conn, String key) throws Exception {
        String sql = "SELECT Tvalue FROM meta WHERE key = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, key);
        ResultSet rs = stmt.executeQuery();
        return rs.getString(1);

    }

    protected void Update_DB_Status(Connection conn, String key, String value) throws Exception {
        String sql = "UPDATE meta SET Tvalue = ? WHERE key = ?";
        PreparedStatement update = conn.prepareStatement(sql);
        update.setString(1, value);
        update.setString(2, key);
        update.executeUpdate();
    }

        // ===== DELETE ENTRY ===== ----- Yup
    protected void deleteEntry(Connection conn, int id) throws Exception {
        String sql = "DELETE FROM vault WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, id);
        stmt.executeUpdate();
    }
    
    // ===== User DELETE ===== ----- Yup
    protected void userdelEntry(Connection conn, String user_id) throws Exception {
        String sql = "DELETE FROM users WHERE user_id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, user_id);
        stmt.executeUpdate();
    }
}
