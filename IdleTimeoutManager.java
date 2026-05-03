import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.*;

//
// Monitors user inactivity scoped to a specific JFrame and its contents.
//
// Timer runs continuously — never pauses for lost focus or backgrounding.
// If the app is abandoned (minimized, switched away, forgotten), it WILL timeout.
// This is intentional security behaviour — abandoned sessions are a liability.
//
// Tracks:
//   - Mouse movement/clicks within the frame's content pane
//   - Keyboard input directed at any component in the frame
//   - Dynamically added components at runtime
//
public class IdleTimeoutManager {

    // Idle threshold — 10 minutes
    private static final long IDLE_TIMEOUT_MINUTES = 10;

    // Scheduler drives the timeout countdown — single daemon thread
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "idle-timeout-thread");
        t.setDaemon(true); // Don't block JVM shutdown if somehow still running
        return t;
    });

    // Hold reference so we can cancel + reschedule on activity
    private ScheduledFuture<?> timeoutTask;

    // Reference to master password for secure wipe on timeout
    private final char[] masterPassword;

    // The parent frame — scopes all listeners and hosts the warning dialog
    private final JFrame parentFrame;

    /**
     * @param parentFrame    Main application window to scope listeners to
     * @param masterPassword Sensitive credential — will be wiped on shutdown
     */
    public IdleTimeoutManager(JFrame parentFrame, char[] masterPassword) {
        this.parentFrame = parentFrame;
        this.masterPassword = masterPassword;
    }

    /**
     * Start monitoring. Call once after your JFrame is visible.
     * Timer starts immediately and never pauses — intentional security behaviour.
     */
    public void start() {
        attachContentPaneListeners();
        scheduleTimeout();

        System.out.println("[IdleTimeout] Started — will shutdown after "
            + IDLE_TIMEOUT_MINUTES + " min of inactivity.");
    }

    /**
     * Attach mouse and key listeners to the frame's content pane and all
     * child components recursively — covers the full window hierarchy.
     */
    private void attachContentPaneListeners() {
        // Mouse listener — resets timer on any click or movement within the frame
        MouseAdapter mouseActivity = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e)   { resetTimeout(); }
            @Override public void mouseDragged(MouseEvent e) { resetTimeout(); }
            @Override public void mousePressed(MouseEvent e) { resetTimeout(); }
        };

        // Key listener — resets timer on any keystroke directed at the frame
        KeyAdapter keyActivity = new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { resetTimeout(); }
        };

        // Attach to the content pane and all current children recursively
        attachToComponentTree(parentFrame.getContentPane(), mouseActivity, keyActivity);

        // Also attach directly to the frame itself for window-level events
        parentFrame.addKeyListener(keyActivity);
        parentFrame.addMouseMotionListener(mouseActivity);
        parentFrame.addMouseListener(mouseActivity);

        // Watch for new components added dynamically at runtime
        parentFrame.getContentPane().addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                // Recursively attach to any newly added component subtree
                attachToComponentTree(e.getChild(), mouseActivity, keyActivity);
            }
        });
    }

    /**
     * Recursively attach mouse and key listeners to a component and all its children.
     * Ensures no component in the hierarchy is missed.
     *
     * @param component  Root of the subtree to attach to
     * @param mouse      Shared mouse adapter
     * @param key        Shared key adapter
     */
    private void attachToComponentTree(Component component,
                                        MouseAdapter mouse,
                                        KeyAdapter key) {
        // Attach listeners to this component
        component.addMouseListener(mouse);
        component.addMouseMotionListener(mouse);
        component.addKeyListener(key);

        // Recurse into children if this is a container
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                attachToComponentTree(child, mouse, key);
            }
        }
    }

    /**
     * Cancel the pending timeout and schedule a fresh one.
     * Called every time user activity is detected.
     */
    private synchronized void resetTimeout() {
        cancelTimeout();
        scheduleTimeout();
    }

    /**
     * Cancel the currently scheduled timeout task if one is pending.
     */
    private synchronized void cancelTimeout() {
        if (timeoutTask != null && !timeoutTask.isDone()) {
            timeoutTask.cancel(false); // Don't interrupt if somehow mid-run
        }
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

        // Marshal back to EDT for UI interaction before shutdown
        SwingUtilities.invokeLater(() -> {
            // Non-blocking warning dialog — auto-dismisses after 3 seconds
            JOptionPane pane = new JOptionPane(
                "Session timed out due to inactivity.\nShutting down securely.",
                JOptionPane.WARNING_MESSAGE
            );
            JDialog dialog = pane.createDialog(parentFrame, "Session Timeout");
            dialog.setModal(false); // Non-blocking so Timer can close it
            dialog.setVisible(true);

            // Auto-dismiss and exit — gives user a moment to see the message
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
     * Shutdown hook registered in main will also fire after System.exit().
     */
    private void performSecureShutdown() {
        // SECURITY CRITICAL: Wipe master password before exit
        // Safe to call twice — shutdown hook will zero it again, harmless
        Backend.wipeCharArray(masterPassword);

        System.out.println("[IdleTimeout] Secure wipe complete. Exiting.");
        System.exit(0); // Triggers registered shutdown hook
    }

    /**
     * Stop monitoring without shutting down.
     * Use when user re-authenticates or during testing.
     */
    public void stop() {
        cancelTimeout();
        scheduler.shutdownNow();
        System.out.println("[IdleTimeout] Idle monitoring stopped.");
    }
}