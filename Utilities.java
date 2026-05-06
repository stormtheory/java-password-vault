public class Utilities 
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
}
