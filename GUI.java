import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
//import java.awt.event.*;
import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;

public class GUI {
    
// ===== CONFIG =====
    private int PASSWORD_LENGTH = 8;
    private String DATABASE_VER = "0";
    private String DATABASE_TYPE = "s";
    protected String sensitivityLevel = "MEDIUM"; //DEFAULT

    private int CLIPBOARD_CLEAR_SEC = 60_000;

// ===== DEFAULT FIELDS ======
    public boolean DEBUG = false; //true or false, set to false before production
    
    public boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
    private char[] masterPassword = new char[0];
    public boolean passwordGood = false;
    private ImageIcon dialogIcon = null;
    protected Backend backend = new Backend();
    private Connection conn;
    private JTable table;
    private DefaultTableModel model;
    private List<Backend.Credential> credentials = new ArrayList<>();
    
// ======= MAIN ====================
    public static void main(String[] args) throws Exception {
        if (System.getProperty("nativeAccessEnabled") == null) {
        String java = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");

        // Relaunch the JVM with the required flags
        ProcessBuilder pb = new ProcessBuilder(
            java,
            "--enable-native-access=ALL-UNNAMED",
            "-Djava.io.tmpdir=" + System.getProperty("user.dir"),
            "-DnativeAccessEnabled=true", // prevents infinite relaunch loop
            "-cp", classpath,
            "GUI"
        );
        pb.inheritIO(); // pass through all output/input
        Process p = pb.start();
        System.exit(p.waitFor()); // exit current process, return child exit code
    }

        SwingUtilities.invokeLater(() -> {
            try {
                //System.out.println(System.getProperty("java.class.path"));
                new GUI().start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    protected void start() throws Exception {
        //java.io.File dbFile = new java.io.File("vault.db");
        String vaultPath = System.getProperty("user.home") + "/Documents/vault.db";
        java.io.File dbFile = new java.io.File(vaultPath);
        boolean isNew = !dbFile.exists();

        // ===== LOAD ICONS =====
        // Load multiple sizes - OS picks the best one for taskbar, alt-tab, title bar etc.
            List<Image> icons = new ArrayList<>();

            String[] iconSizes = {"icons/icon_16.png", "icons/icon_32.png",
                                "icons/icon_64.png", "icons/icon_256.png"};

            for (String path : iconSizes) {
                File iconFile = new File(path);
                if (iconFile.exists()) {
                    // Add each size to the list for the taskbar/title bar
                    icons.add(new ImageIcon(iconFile.getAbsolutePath()).getImage());

                    // Use the 256px version for dialogs - only set it once when we find that file
                    if (path.contains("256") && dialogIcon == null) {
                        Image scaled = new ImageIcon(iconFile.getAbsolutePath())
                            .getImage()
                            .getScaledInstance(64, 64, Image.SCALE_SMOOTH); // scale down for dialogs
                        dialogIcon = new ImageIcon(scaled);
                    }
                } else {
                    if (DEBUG) System.out.println("[GUI] Icon not found: " + iconFile.getAbsolutePath());
                }
            }
    
        // ===== NO VAULT FOUND - ask to create new or locate existing =====
        if (isNew) {
            Object[] options = {"Create New Vault", "Locate Existing Vault", "Cancel"};
            int choice = JOptionPane.showOptionDialog(null,
                "No vault found in Documents.\nCreate a new vault or locate an existing one?",
                "Vault Not Found",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                dialogIcon,
                options,
                options[0]);

            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                // User cancelled - exit cleanly
                System.exit(0);

            } else if (choice == 1) {
                // ===== FILE SELECTOR - locate existing vault =====
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Locate your vault.db file");
                fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "SQLite Database (*.db)", "db")); // only show .db files
                fileChooser.setCurrentDirectory(new java.io.File(
                    System.getProperty("user.home") + "/Documents")); // start in Documents

                int result = fileChooser.showOpenDialog(null);

                if (result == JFileChooser.APPROVE_OPTION) {
                    vaultPath = fileChooser.getSelectedFile().getAbsolutePath();
                    dbFile = new java.io.File(vaultPath);
                    isNew = false;
                } else {
                    System.exit(1);
                }
            }
        }

// ===== MASTER PASSWORD PROMPT =====
        
        // ======= HOOK for SHUTDOWN =======
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Shutdown Hook] Running cleanup command...");
 
            try {
                // SECURITY CRITICAL: Wipe master password from memory before JVM exits
                // Prevents sensitive data staying in memory heap which mitigates memory dump attacks
                Backend.wipeCharArray(masterPassword);

                // Using ProcessBuilder is safer than Runtime.exec() — avoids shell injection
                // Say goodnight to tell me we are gracefully shutdown.
                ProcessBuilder pb = isWindows
                    ? new ProcessBuilder("cmd.exe", "/c", "Goodbye...")
                    : new ProcessBuilder("echo", "Goodbye...");
                    // e.g. "bash", "-c", "your-script.sh"
                    // e.g. "python3", "/opt/cleanup.py"
 
                // Inherit stdout/stderr so output is visible in the terminal
                pb.inheritIO();
 
                Process process = pb.start();
                int exitCode = process.waitFor();
 
                System.out.println(exitCode);
 
            } catch (Exception e) {
                // Log but don't rethrow — throwing inside a hook is silently ignored
                System.err.println("[Shutdown Hook] Failed to run command: " + e.getMessage());
            }
        }, "shutdown-hook-thread"));


        if (isNew) {
            while (true) {
                // ===== CREATE PASSWORD =====
                // if no vault.db then prompt for a password and make a vault
                JPasswordField pf1 = new JPasswordField();
                JPasswordField pf2 = new JPasswordField();

                Object[] msg = {
                        "Create Master Password:", pf1,
                        "Confirm Password:", pf2
                };

                int ok = JOptionPane.showConfirmDialog(
                        null,
                        msg,
                        "Create Master Password",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );

                if (ok != JOptionPane.OK_OPTION) System.exit(0);

                masterPassword = pf1.getPassword();
                char[] p2 = pf2.getPassword();

                if (!java.util.Arrays.equals(masterPassword, p2)) {
                    passwordGood = false;
                    JOptionPane.showMessageDialog(null, "Passwords do not match!");
                    Backend.wipeCharArray(masterPassword);
                    Backend.wipeCharArray(p2);
                } else if (masterPassword.length == 0) {
                    // Password cannot be empty
                    JOptionPane.showMessageDialog(null, "Password cannot be empty!", 
                    "Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                    Backend.wipeCharArray(masterPassword);
                    Backend.wipeCharArray(p2);
                 } else if (masterPassword.length < PASSWORD_LENGTH) {
                    // Enforce minimum length
                     JOptionPane.showMessageDialog(null, "Password must be at least 8 characters!", 
                      "Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                      Backend.wipeCharArray(masterPassword);
                      Backend.wipeCharArray(p2);
                } else {
                    // All checks passed
                    passwordGood = true;
                    break;
                }
                Backend.wipeCharArray(p2);
            }

        } else {
            // ===== AT STARTUP - ENTER PASSWORD =====
            JPasswordField pf = new JPasswordField();

            int ok = JOptionPane.showConfirmDialog(
                    null,
                    pf,
                    "Enter Master Password",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (ok != JOptionPane.OK_OPTION) System.exit(0);

            masterPassword = pf.getPassword();
            passwordGood = true;
        }

        if (passwordGood) {
            if (masterPassword != null && masterPassword.length != 0) {          
                
                

                // ===== DB CONNECT =====
                // Load the driver!!! Go!
                Class.forName("org.sqlite.JDBC");
                
                // Then connect - Got to connect to the database, best part auto-magically
                conn = DriverManager.getConnection("jdbc:sqlite:" + vaultPath);

                if (isNew) {
                    backend.BuildDatabase(conn, DATABASE_VER, DATABASE_TYPE);
                }

                // ===== GET SALT ===== #### Pulled from vault.db radom to each vault
                byte[] salt = backend.getOrCreateSalt(conn);
                if (DEBUG) {System.out.println("[GUI] get Salt: " + salt);}
                
                // ===== INIT BACKEND =====
                // A salt is just random data added to a password before key derivation --- prevents Rainbow Table attacks
                backend.GetFiredUp(masterPassword, salt, conn);

                // ===== LOAD DATA =====
                // Loads all data into an ArraryList
                
                try {
                credentials = backend.loadAll(conn);
                 } catch (javax.crypto.AEADBadTagException e) {
                    // Tag mismatch is when there is a wrong key or I guess corrupted data
                    JOptionPane.showMessageDialog(null,
                        "Failed to decrypt - wrong master password or corrupted data.",
                        "Decryption Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                    if (DEBUG) e.printStackTrace();
                    System.exit(1);

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null,
                        "Unexpected error reading password entry.",
                        "Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                    if (DEBUG) e.printStackTrace();
                    System.exit(1);
                }
            } else {System.exit(1);}
        } else {System.exit(1);}


        // ===== BUILD UI =====
        // If we build it, they will come...
        JFrame frame = new JFrame("Password Vault");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        IdleTimeoutManager idleManager = new IdleTimeoutManager(frame, masterPassword);
        idleManager.start();

        model = new DefaultTableModel(new Object[]{"ID", "Tag", "Username", "Password", "Actions"}, 0) {
            public boolean isCellEditable(int row, int column) {
                return column == 4 || column == 5; // this is for the Buttons // Copy and Show
            }
        };

        // Apply icons to frame - must be done before setVisible(true)
        if (!icons.isEmpty()) {
            frame.setIconImages(icons);
        } else {
            System.err.println("[GUI] Warning: No icons loaded - using default Java icon");
        }

        table = new JTable(model);

        refreshTable();

        // Hide ID column
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);

        // ===== WIRE BOTH ACTION COLUMNS TOGETHER AND TO THE ACTIONS =====
        // Widen both action columns slightly to fit two buttons in the renderer
        table.setRowHeight(35);
        table.getColumn("Actions").setPreferredWidth(150);
        table.getColumn("Actions").setCellRenderer(new ButtonRenderer());
        table.getColumn("Actions").setCellEditor(new ButtonEditor(new JCheckBox()));

        JScrollPane scroll = new JScrollPane(table);

        // ===== BUTTONS =====
        JButton addBtn = new JButton("Add Entry");
        JButton delBtn = new JButton("Delete Selected");

        addBtn.addActionListener(e -> addEntry());
        delBtn.addActionListener(e -> deleteEntry());

        JPanel panel = new JPanel();
        panel.add(addBtn);
        panel.add(delBtn);

        frame.add(scroll, BorderLayout.CENTER);
        frame.add(panel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    // ===== REFRESH TABLE =====
    private void refreshTable() {
        model.setRowCount(0);

        for (Backend.Credential c : credentials) {
            model.addRow(new Object[]{
                    c.id,
                    c.tag,
                    c.username,
                    "*****",
                    "Copy",
                    "Show"
            });
        }
    }

    // ===== COPY PASSWORD TO CLIPBOARD BUTTON =====
    private void copyPassword(int row) {
        try {
            Backend.Credential c = credentials.get(row);

            char[] password = backend.decryptData(c.encryptedPassword, c.iv);

            // This puts the password into system clipboard
            java.awt.datatransfer.StringSelection selection =
                new java.awt.datatransfer.StringSelection(new String(password));
            java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(selection, null);

            // Make sure to wipe after
            Backend.wipeCharArray(password);

                // Auto-clears clipboard after 60s
                // THIS WAS PRETTY COOL TO SEE HOW TO MAKE A THREAD IN JAVA!
                javax.swing.Timer wipeclip = new javax.swing.Timer(CLIPBOARD_CLEAR_SEC, ev -> {
                    java.awt.Toolkit.getDefaultToolkit()
                        .getSystemClipboard()
                        .setContents(new java.awt.datatransfer.StringSelection(""), null);
                });
                wipeclip.setRepeats(false); // fire once only, not on a loop
                wipeclip.start();

            // Tell the user it worked
            JOptionPane.showMessageDialog(null,
            "Password copied to clipboard.\n  Clipboard will clear in 1 minute.", "Copied",
            JOptionPane.INFORMATION_MESSAGE, dialogIcon);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // ===== SHOW PASSWORD BUTTON =====
    private void showPassword(int row) {
        try {
            Backend.Credential c = credentials.get(row);

            char[] password = backend.decryptData(c.encryptedPassword, c.iv);

            // Creates a pop-up of the plain-text password
            JOptionPane.showMessageDialog(null, new String(password), "Password",
                    JOptionPane.INFORMATION_MESSAGE, dialogIcon);

            // Make sure to wipe after
            Backend.wipeCharArray(password);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== ADD ENTRY BUTTON =====
    private void addEntry() {
        JTextField tagField = new JTextField();
        JTextField userField = new JTextField();
        JPasswordField passField = new JPasswordField();

        Object[] message = {
                "Tag/URL:", tagField,
                "Username:", userField,
                "Password:", passField
        };

        int option = JOptionPane.showConfirmDialog(null, message, "Add Entry",
                JOptionPane.OK_CANCEL_OPTION);

        if (option == JOptionPane.OK_OPTION) {
            try {
                backend.addEntry(conn, tagField.getText().toCharArray(), userField.getText().toCharArray(), passField.getPassword());

                credentials = backend.loadAll(conn);
                refreshTable();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ===== DELETE ENTRY BUTTON =====
    private void deleteEntry() {
        int row = table.getSelectedRow();
        if (row == -1) return;

        int confirm = JOptionPane.showConfirmDialog(null,
                "Delete selected entry?", "Confirm",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                int id = (int) model.getValueAt(row, 0);
                backend.deleteEntry(conn, id);

                credentials = backend.loadAll(conn);
                refreshTable();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // ===== BUTTON RENDERER =====
    // Renders a small panel with Copy and Show side by side for each row
    class ButtonRenderer extends JPanel implements TableCellRenderer {
    private final JButton copyBtn = new JButton("Copy");
    private final JButton showBtn = new JButton("👁 Show");
    
    public ButtonRenderer() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 4, 2));
        add(copyBtn);
        add(showBtn);
        setOpaque(true); // required so table background doesn't bleed through
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int col) {
        // Match row selection highlight so panel doesn't look detached
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return this;
        }
    }

    // ===== BUTTON EDITOR =====
    // Single editor panel with both Show and Copy wired to their respective actions
        class ButtonEditor extends DefaultCellEditor {
            private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
            private final JButton copyBtn = new JButton("Copy");
            private final JButton showBtn = new JButton("Show\nPassword");
            private int row;

            public ButtonEditor(JCheckBox checkBox) {
                super(checkBox);

                // Copy - silently writes decrypted password to clipboard, no dialog shown
                copyBtn.addActionListener(e -> {
                    fireEditingStopped(); // commit edit before clipboard write
                    copyPassword(row);
                });

                // Show - reveals password in a dialog
                showBtn.addActionListener(e -> {
                    fireEditingStopped(); // commit edit before opening dialog
                    showPassword(row);
                });
                panel.add(copyBtn);
                panel.add(showBtn);
            }

            public Component getTableCellEditorComponent(JTable table, Object value,
                                                        boolean isSelected, int row, int col) {
                this.row = row; // capture row so both buttons know which credential to act on
                return panel;
            }

            public Object getCellEditorValue() {
                return "Action"; // return value unused but must not be null
            }
        }
}