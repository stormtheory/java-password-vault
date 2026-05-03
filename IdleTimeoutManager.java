import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

//
// Monitors user inactivity across the entire Swing application.
// Triggers a secure shutdown after a configurable idle period.
//
// Listens globally via Toolkit.getDefaultToolkit().addAWTEventListener()
// so it catches events from ALL windows/components — no per-component wiring needed.
//
public class IdleTimeoutManager {

    // Idle threshold — 5 minutes
    private static final long IDLE_TIMEOUT_MINUTES = 5;

    // Scheduler drives the timeout countdown — single thread is sufficient
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "idle-timeout-thread");
        t.setDaemon(true); // Don't block JVM shutdown if somehow still running
        return t;
    });

    // Hold reference so we can cancel + reschedule on activity
    private ScheduledFuture<?> timeoutTask;

    // Reference to master password for secure wipe on timeout
    private final char[] masterPassword;

    // The parent frame — used to show warning dialog before full shutdown
    private final JFrame parentFrame;

    /**
     * @param parentFrame    Main application window (for optional warning dialog)
     * @param masterPassword Sensitive credential — will be wiped on shutdown
     */
    public IdleTimeoutManager(JFrame parentFrame, char[] masterPassword) {
        this.parentFrame = parentFrame;
        this.masterPassword = masterPassword;
    }

    /**
     * Start monitoring. Call once after your main window is visible.
     * Listens for mouse moves, clicks, and keystrokes globally.
     */
    public void start() {
        // Schedule the initial timeout
        scheduleTimeout();

        // AWTEventListener intercepts ALL events across ALL Swing components globally
        // MOUSE_MOTION_EVENT_MASK catches moves/drags — not just clicks
        Toolkit.getDefaultToolkit().addAWTEventListener(activityListener,
            AWTEvent.MOUSE_EVENT_MASK |
            AWTEvent.MOUSE_MOTION_EVENT_MASK |
            AWTEvent.KEY_EVENT_MASK
        );

        System.out.println("[IdleTimeout] Started — will shutdown after " + IDLE_TIMEOUT_MINUTES + " min of system inactivity.");
    }

    /**
     * Global AWT event listener — resets the timer on any user interaction.
     * Fires on the EDT, so keep it lightweight (just reschedule).
     */
    private final AWTEventListener activityListener = event -> {
        // Only reset on actual user-initiated events — ignore programmatic ones
        if (event instanceof MouseEvent || event instanceof KeyEvent) {
            resetTimeout();
        }
    };

    /**
     * Cancel the pending timeout and schedule a fresh one.
     * Called every time user activity is detected.
     */
    private synchronized void resetTimeout() {
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false); // Don't interrupt if somehow mid-run
        }
        scheduleTimeout();
    }

    /**
     * Schedule the shutdown to fire after IDLE_TIMEOUT_MINUTES of no activity.
     */
    private synchronized void scheduleTimeout() {
        timeoutTask = scheduler.schedule(
            this::onIdleTimeout,
            IDLE_TIMEOUT_MINUTES,
            TimeUnit.MINUTES
        );
    }

    /**
     * Called when idle timeout fires.
     * Shows a brief warning on the EDT, then performs secure shutdown.
     */
    private void onIdleTimeout() {
        System.out.println("[IdleTimeout] Idle limit reached — initiating secure shutdown.");

        // Marshal back to EDT for any UI interaction before shutdown
        SwingUtilities.invokeLater(() -> {
            // Optional: show a non-blocking notice (auto-dismisses after 3s via Timer)
            JOptionPane pane = new JOptionPane(
                "Session timed out due to inactivity.\nShutting down securely.",
                JOptionPane.WARNING_MESSAGE
            );
            JDialog dialog = pane.createDialog(parentFrame, "Session Timeout");
            dialog.setModal(false); // Non-blocking so Timer can close it
            dialog.setVisible(true);

            // Auto-dismiss dialog and then exit — gives user a moment to see it
            new Timer(3000, e -> {
                dialog.dispose();
                performSecureShutdown();
            }) {{
                setRepeats(false); // Fire once only
                start();
            }};
        });
    }

    /**
     * Wipe sensitive data and exit cleanly.
     * Shutdown hook (registered in main) will also fire after System.exit().
     */
    private void performSecureShutdown() {
        // SECURITY CRITICAL: Wipe master password before exit
        // The shutdown hook will also call this
        Backend.wipeCharArray(masterPassword);

        System.out.println("[IdleTimeout] Secure wipe complete. Exiting.");
        System.exit(0); // Triggers registered shutdown hook
    }

    /**
     * Call this if you want to stop monitoring without shutting down
     * (e.g. user re-authenticates, or during testing).
     */
    public void stop() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
        scheduler.shutdownNow();
        System.out.println("[IdleTimeout] Idle monitoring stopped.");
    }
}