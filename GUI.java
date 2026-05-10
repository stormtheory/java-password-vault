import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.ItemEvent;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.DefaultCellEditor;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

public class GUI {
    
// ===== CONFIG =====
    private static final int PASSWORD_LENGTH = 8;
    private static final String DATABASE_VER = "0";
    private static final int CLIPBOARD_CLEAR_SEC = 60_000;

// ===== DEFAULT FIELDS ======
    public static boolean DEBUG = false; //true or false, set to false before production
    
    private static String DATABASE_TYPE;
    private String username = "single-user";
    protected String VaultLevel;
    protected static Boolean arg_vaultPath = false;
    protected static String vaultPath;
    public boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
    private static char[] masterPassword = new char[0];
    public boolean passwordGood = false;
    private ImageIcon dialogIcon = null;
    protected Backend backend = new Backend();
    protected static DatabaseUtilities databaseutilities = new DatabaseUtilities();
    protected static Connection conn;
    private JTable table;
    private DefaultTableModel model;
    private List<Backend.Credential> credentials = new ArrayList<>();
    
// ======= MAIN ====================
    public static void main(String[] args) throws Exception {
        
    DatabaseUtilities.configureSQLiteTmpDir();

    if (System.getProperty("nativeAccessEnabled") == null) {
        String java = ProcessHandle.current().info().command().orElse("java");
        String classpath = System.getProperty("java.class.path");
        List<String> cmd = new ArrayList<>(Arrays.asList(
            java,
            "--enable-native-access=ALL-UNNAMED",
            "-Dorg.sqlite.tmpdir=" + System.getProperty("org.sqlite.tmpdir"),
            "-DnativeAccessEnabled=true",
            "-cp", classpath,
            "GUI"
        ));
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
        System.exit(p.waitFor());
    }

    for (int i = 0; i < args.length; i++) {
        switch (args[i]) {
            case "--port":
                int port = Integer.parseInt(args[++i]); // grab next arg as value
                break;
            case "--vault_file":
                arg_vaultPath = true;
                vaultPath = args[++i];
                System.out.println(vaultPath);
                break;
            case "--vault-file":
                arg_vaultPath = true;
                vaultPath = args[++i];
                System.out.println(vaultPath);
                break;
            case "-d":
                DEBUG = true;
                break;
            case "-h":
                helpMenu();
                break;
        }
    }

        SwingUtilities.invokeLater(() -> {
            try {
                new GUI().start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void helpMenu(){
        System.out.println("""
        ---- Help Menu ---

            --vault_file [file_dir_path]

            -d              Debug
            -h              Help Menu

        """);
        System.exit(0);
    }

    protected void start() throws Exception {
        //java.io.File dbFile = new java.io.File("vault.db");
        if (!arg_vaultPath) 
            {
                vaultPath = System.getProperty("user.home") + "/Documents/vault.db";
            }
        //System.out.println(vaultPath);
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
                "No vault found at "+ vaultPath +"\nCreate a new vault or locate an existing one?",
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

        //Load the driver!!! Go!
        Class.forName("org.sqlite.JDBC");

        // ===== Vault Checks ========
        if (!isNew) {        
            // Then connect - Got to connect to the database, best part auto-magically
            conn = DriverManager.getConnection("jdbc:sqlite:" + vaultPath);
            DATABASE_TYPE = DatabaseUtilities.Pull_DB_Text_Meta_item(conn, "type");
            VaultLevel = DatabaseUtilities.Pull_DB_Text_Meta_item(conn, "vault_level");
        }

        // ======= HOOK for SHUTDOWN =======
        DatabaseUtilities.registerShutdownHook(isWindows, DEBUG);

        // ===== Create New Vault =============================================
        // ===== MASTER PASSWORD PROMPT =====
        if (isNew) {
            passwordGood = createNewMasterPass(conn, true);
        } else {
        // ===== AT STARTUP ===================================================
            JPasswordField pf = new JPasswordField();
            
            if (DATABASE_TYPE.equals("m")) {
                JTextField usernameField = new JTextField();
                Object[] msg = {
                    "Username:", usernameField,
                    "Password:", pf
                };

                int ok = JOptionPane.showConfirmDialog(
                    null,
                    msg,
                    "Login",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
                );
                if (DATABASE_TYPE.equals("m")) {username = usernameField.getText();}
                if (ok != JOptionPane.OK_OPTION) System.exit(0);} 
                else {
                Object[] msg = {
                "Master Password:", pf
            };
                   int ok = JOptionPane.showConfirmDialog(
                    null,
                    msg,
                    "Login",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
                );
                if (ok != JOptionPane.OK_OPTION) System.exit(0);
            }
            masterPassword = pf.getPassword();
            passwordGood = true;
        }
        if (passwordGood) {
            if (masterPassword != null && masterPassword.length != 0) {
                if (isNew) {
                    conn = DriverManager.getConnection("jdbc:sqlite:" + vaultPath);
                    backend.BuildDatabase(conn, username, DATABASE_VER, DATABASE_TYPE, VaultLevel);
                }
                // ===== GET SALT ===== #### Pulled from vault.db radom to each vault
                byte[] vault_salt = backend.getOrCreateVaultSalt(conn);
                
                // ===== INIT BACKEND =====
                // A salt is just random data added to a password before key derivation --- prevents Rainbow Table attacks
                backend.GetFiredUp(masterPassword, vault_salt, conn, username, DATABASE_TYPE);
                Backend.wipeCharArray(masterPassword);

                // ===== LOAD DATA =====
                // Loads all data into an ArraryList
                
                try {
                credentials = backend.loadAll(conn);
                 } catch (javax.crypto.AEADBadTagException e) {
                    // Tag mismatch is when there is a wrong key or I guess corrupted data
                    JOptionPane.showMessageDialog(null,
                        "Failed to decrypt - wrong master password or corrupted data.",
                        "Decryption Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                    System.exit(1);

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null,
                        "Unexpected error reading password entry.",
                        "Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                    System.exit(1);
                }
            } else {System.exit(1);}
        } else {System.exit(1);}


        // ===== BUILD UI =====
        // If we build it, they will come...
        JFrame frame = new JFrame("Password Vault");
        if (DATABASE_TYPE.equals("m")){
            frame.setTitle("Password Vault:   " + username);
        }
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        IdleTimeoutManager idleManager = new IdleTimeoutManager(frame);
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
        JButton changeMasterPassBtn = new JButton("Account Password: " + username);
        JButton useraddBtn = new JButton("Add User");
        JButton userdelBtn = new JButton("Delete User");
        

        addBtn.addActionListener(e -> addEntry());
        delBtn.addActionListener(e -> deleteEntry());
        changeMasterPassBtn.addActionListener(e -> changeMasterPass(username));
        useraddBtn.addActionListener(e -> useraddEntry(username));
        userdelBtn.addActionListener(e -> userdelEntry(username));

        JPanel panel = new JPanel();

        panel.add(addBtn);
        panel.add(delBtn);
        panel.add(changeMasterPassBtn);
        if (DATABASE_TYPE.equals("m")) {
            panel.add(useraddBtn);
            panel.add(userdelBtn);
        } else {
            panel.add(useraddBtn).setVisible(false);
            panel.add(userdelBtn).setVisible(false);
        }

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
                    new String(c.tag),
                    new String(c.username),
                    "*****",
                    "Copy",
                    "Show"
            });
        //c.wipe();
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
        JTextField noteField = new JTextField();
        JPasswordField passField = new JPasswordField();

        // ========= Password visibility toggle =============================
        JCheckBox showPass = new JCheckBox("Show");
        showPass.addActionListener(e ->
            passField.setEchoChar(showPass.isSelected() ? (char) 0 : '•')
        );

        // ====== Generator options =================================
        JCheckBox useABC     = new JCheckBox("ABC", true);
        JCheckBox useNumbers = new JCheckBox("123", true);
        JCheckBox useSpecial = new JCheckBox("!@#", false);
        SpinnerNumberModel lenNumModel = new SpinnerNumberModel(18, 8, 66, 1);
        JSpinner lenNumSpinner = new JSpinner(lenNumModel);
        lenNumSpinner.setPreferredSize(new Dimension(55, 24));

        // ====== Generator options row ===========================================
        JPanel genOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        genOptions.add(new JLabel("Len:"));
        genOptions.add(lenNumSpinner);
        genOptions.add(useABC);
        genOptions.add(useNumbers);
        genOptions.add(useSpecial);

        // ======= Gen button =============
        JButton genBtn = new JButton("Generate");
        genBtn.addActionListener(e -> {
            int length = (int) lenNumSpinner.getValue();
            char[] generated = DatabaseUtilities.generatePassword(length,useABC.isSelected(),useNumbers.isSelected(),useSpecial.isSelected());
            passField.setText(new String(generated));
            Arrays.fill(generated, '\0'); // zero out
        });

        // ========== Row: password field + show checkbox =============================
        JPanel passRow = new JPanel(new BorderLayout(4, 0));
        passRow.add(passField, BorderLayout.CENTER);
        passRow.add(showPass,  BorderLayout.EAST);

        Object[] message = {
            "Tag/URL:",   tagField,
            "Username:",  userField,
            "Password:",  passRow,
            genOptions,
            genBtn,
            "Notes:", noteField
        };

        int option = JOptionPane.showConfirmDialog(null, message, "Add Entry",
            JOptionPane.OK_CANCEL_OPTION);

        if (option == JOptionPane.OK_OPTION) {
            try {
                backend.addEntry(conn, tagField.getText().toCharArray(), userField.getText().toCharArray(), passField.getPassword(), noteField.getText().toCharArray(), DATABASE_TYPE);
                credentials = backend.loadAll(conn);
                refreshTable();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void changeMasterPass(String username)
        {
            passwordGood = createNewMasterPass(conn, false);
            if (passwordGood){
                try {
                backend.changeMasterPass(conn, masterPassword,username);
                JOptionPane.showMessageDialog(null, "Changed " + username + " account password.   You must logout and back in again.","Success", JOptionPane.INFORMATION_MESSAGE, dialogIcon);
                } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Failed to change " + username + " account password","Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                }
                Backend.wipeCharArray(masterPassword);
            }
        }

    protected boolean testPasswordStrength( char[] password, char[] p2){ 

            if (!java.util.Arrays.equals(password, p2)) {
                JOptionPane.showMessageDialog(null, "Passwords do not match!");
                Backend.wipeCharArray(password);
                Backend.wipeCharArray(p2);
                return false;
            } else if (masterPassword.length == 0) {
                // Password cannot be empty
                JOptionPane.showMessageDialog(null, "Password cannot be empty!",
                    "Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                Backend.wipeCharArray(masterPassword);
                Backend.wipeCharArray(p2);
                return false;
            } else if (masterPassword.length < PASSWORD_LENGTH) {
                // Enforce minimum length
                JOptionPane.showMessageDialog(null, "Password must be at least " + PASSWORD_LENGTH + " characters!",
                    "Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                Backend.wipeCharArray(masterPassword);
                Backend.wipeCharArray(p2);
                return false;
            } else {
                /// Can not wipe Passsword here
                Backend.wipeCharArray(p2);
                return true;
            }
        }

    protected boolean createNewMasterPass(Connection conn, boolean createVault){
        int ok;
        while (true) {
            // ===== CREATE PASSWORD =====
        // If no vault.db — prompt for password and vault type
        JPasswordField pf1      = new JPasswordField(20);
        JPasswordField pf2      = new JPasswordField(20);
        JTextField     usernameField = new JTextField(20);
        JLabel         more_space   = new JLabel(" ");
        JLabel         more_space1   = new JLabel(" ");
        JLabel         more_space2   = new JLabel(" ");
        JLabel         ufl_spacer   = new JLabel(" ");
        JLabel         uf_spacer   = new JLabel(" ");
        JLabel         usernameLabel = new JLabel("Username:");
        JLabel         type_of_vault_label = new JLabel("Type of Vault:");

        usernameField.setVisible(false);
        usernameLabel.setVisible(false);

        JComboBox<String> DataBaseSelector = new JComboBox<>(new String[]{
            "Single User - One password, One key",
            "Multi-User  - Many usernames/passwords, One key"
        });
        DataBaseSelector.setSelectedIndex(0);

        JComboBox<String> profileSelector = new JComboBox<>(new String[]{
            "Minimum  - Low risk, high throughput (OWASP 2023)",
            "Balanced - Most applications (RFC 9106)",
            "High     - Sensitive credentials (RFC 9106)",
            "Paranoid - Vault/master-key grade"
        });
        profileSelector.setSelectedIndex(2);

        if (!createVault) {
            try {
                // Capture the returned value — was previously discarded
                String vault_level = DatabaseUtilities.Pull_DB_Text_Meta_item(conn, "vault_level");
                // Map stored string to combobox index
                int selectedIndex = switch (vault_level.trim()) {
                    case "MINIMUM"  -> 0;
                    case "BALANCED" -> 1;
                    case "HIGH"     -> 2;
                    case "PARANOID" -> 3;
                    // Fallback to High if DB value is unexpected — fail secure, not fail open
                    default -> {
                        System.err.println("Unknown vault_level in DB: " + vault_level + " — defaulting to High");
                        yield 2;
                    }
                };
                profileSelector.setSelectedIndex(selectedIndex);
            } catch (Exception e) {
                System.err.println("Failed to pull DB metadata: " + e.getMessage());
            }
        }

        // Show/hide username field when vault type changes
        type_of_vault_label.setVisible(createVault);
        DataBaseSelector.setVisible(createVault);
        DataBaseSelector.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                boolean multi = DataBaseSelector.getSelectedIndex() == 1;
                usernameLabel.setVisible(multi);
                ufl_spacer.setVisible(!multi);
                usernameField.setVisible(multi);
                uf_spacer.setVisible(!multi);
            }
        });

        Object[] msg = {
            more_space1, more_space1,
                ufl_spacer,ufl_spacer,
            type_of_vault_label,          DataBaseSelector,
            more_space2, more_space2,
                usernameLabel, usernameField,
            "Create Master Password:", pf1,
            "Confirm Password:",       pf2,
            more_space, more_space,
            "Security Profile:",       profileSelector,
                uf_spacer, uf_spacer
        };

        if (createVault){
        ok = JOptionPane.showConfirmDialog(
            null, msg, "Create Vault",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );} else {
        ok = JOptionPane.showConfirmDialog(
            null, msg, "Update Account Password",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );}

        if (ok != JOptionPane.OK_OPTION) System.exit(0);
            masterPassword = pf1.getPassword();
                DATABASE_TYPE = switch (DataBaseSelector.getSelectedIndex()) {
                    case 0  -> "s";
                    case 1  -> "m";
                    default -> "s";
                };
            if (DATABASE_TYPE.equals("m")) {username = usernameField.getText();}
            char[] p2 = pf2.getPassword();
            
            // All checks passed — map selected index to Argon2Profile
                VaultLevel = switch (profileSelector.getSelectedIndex()) {
                    case 0  -> "MINIMUM";
                    case 1  -> "BALANCED";
                    case 2  -> "HIGH";
                    case 3  -> "PARANOID";
                    default -> "HIGH";
                };
            if (!createVault) {
                try {
                    DatabaseUtilities.Update_DB_Text_Meta_item(conn, "vault_level", VaultLevel);
                } catch (Exception e) {
                    System.err.println("Failed to update DB metadata: " + e.getMessage());
                }
            }
            passwordGood = testPasswordStrength( masterPassword , p2);
            Backend.wipeCharArray(p2);
            if (passwordGood){return true;}
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
                databaseutilities.deleteEntry(conn, id);

                credentials = backend.loadAll(conn);
                refreshTable();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

        // ===== USERADD BUTTON =====
    private void useraddEntry(String current_username) {
        JTextField userField = new JTextField();
        JPasswordField passField1 = new JPasswordField();
        JPasswordField passField2 = new JPasswordField();

        Object[] message = {
                "New Username:", userField,
                "Create Password:", passField1,
                "Confirm Password:", passField2
                // ROLE
        };  
        int option = JOptionPane.showConfirmDialog(null, message, "User Add",
                JOptionPane.OK_CANCEL_OPTION);

        
        if (option == JOptionPane.OK_OPTION) {
            boolean newPasswordGood = testPasswordStrength(passField1.getPassword(), passField2.getPassword());
            if (newPasswordGood){
                if (!userField.equals(current_username)){
                    try {
                        backend.useraddEntry(conn, userField.getText(), passField1.getPassword());
                        JOptionPane.showMessageDialog(null, userField.getText() + " added.","Success", JOptionPane.INFORMATION_MESSAGE, dialogIcon);
                    } catch (Exception e) {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(null, "Failed to add: " + userField.getText(),
                        "Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                    }
                }
            } 
        }
    }

    // ===== USER DELETE BUTTON =====
    private void userdelEntry(String current_username) { 
        JTextField userField = new JTextField();
        
        Object[] message = {
                "Username:", userField,
        };

            int option = JOptionPane.showConfirmDialog(null, message, "User Delete",
                    JOptionPane.OK_CANCEL_OPTION);

            if (option == JOptionPane.OK_OPTION) {
                if (!userField.equals(current_username)){
                try {
                    databaseutilities.userdelEntry(conn, userField.getText());
                    JOptionPane.showMessageDialog(null, userField.getText() + " deleted.","Success", JOptionPane.INFORMATION_MESSAGE, dialogIcon);
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Failed to delete: " + userField.getText(),
                    "Error", JOptionPane.ERROR_MESSAGE, dialogIcon);
                }      
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