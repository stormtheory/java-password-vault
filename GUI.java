import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.List;

public class GUI {

    private Backend backend = new Backend();
    private Connection conn;
    private JTable table;
    private DefaultTableModel model;
    private List<Backend.Credential> credentials;

    protected static void main(String[] args) throws Exception {
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
        java.io.File dbFile = new java.io.File("vault.db");
        boolean isNew = !dbFile.exists();

        if (isNew) {
        int choice = JOptionPane.showConfirmDialog(null,
            "No vault found. Create new vault?",
            "First Run",
            JOptionPane.YES_NO_OPTION);

        if (choice != JOptionPane.YES_OPTION) {
            System.exit(0);
        }
        }
    
        // ===== DB CONNECT =====
        // Load the driver!!! Go!
        Class.forName("org.sqlite.JDBC");

        // Then connect - Got to connect to the database, best part auto-magically
        conn = DriverManager.getConnection("jdbc:sqlite:vault.db");
        
        if (isNew) {
            initializeDatabase(conn);
        }

        // ===== GET SALT =====
        byte[] salt = getOrCreateSalt(conn);

        // ===== MASTER PASSWORD PROMPT =====
        char[] masterPassword;

        if (isNew) {
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

            char[] p1 = pf1.getPassword();
            char[] p2 = pf2.getPassword();

            if (!java.util.Arrays.equals(p1, p2)) {
                JOptionPane.showMessageDialog(null, "Passwords do not match!");
                Backend.wipeCharArray(p1);
                Backend.wipeCharArray(p2);
                System.exit(0);
            }

            masterPassword = p1;
            Backend.wipeCharArray(p2);

        } else {
            // ===== ENTER PASSWORD =====
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
        }

        // ===== INIT BACKEND =====
        // A salt is just random data added to a password before key derivation --- prevents Rainbow Table attacks
        backend.initialize(masterPassword, salt);

        // ===== LOAD DATA =====
        // Loads all data into an ArraryList
        credentials = backend.loadAll(conn);

        // ===== BUILD UI =====
        // If we build it, they will come...
        JFrame frame = new JFrame("Password Vault");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        model = new DefaultTableModel(new Object[]{"ID", "Tag", "Username", "Password", "Action"}, 0) {
            public boolean isCellEditable(int row, int column) {
                return column == 4; // only button column
            }
        };

        table = new JTable(model);

        refreshTable();

        // Hide ID column
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);

        // Button renderer/editor
        table.getColumn("Action").setCellRenderer(new ButtonRenderer());
        table.getColumn("Action").setCellEditor(new ButtonEditor(new JCheckBox()));

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
                    "Show"
            });
        }
    }


    private void initializeDatabase(Connection conn) throws Exception {
    Statement stmt = conn.createStatement();

    // Vault table
    stmt.execute("""
        CREATE TABLE vault (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tag TEXT,
            username TEXT,
            password BLOB,
            iv BLOB
        )
    """);

    // Meta table (for salt)
    stmt.execute("""
        CREATE TABLE meta (
            key TEXT PRIMARY KEY,
            value BLOB
        )
    """);
    }

    // ===== SHOW PASSWORD BUTTON =====
    private void showPassword(int row) {
        try {
            Backend.Credential c = credentials.get(row);

            char[] password = backend.decryptPassword(c.encryptedPassword, c.iv);

            JOptionPane.showMessageDialog(null, new String(password), "Password",
                    JOptionPane.INFORMATION_MESSAGE);

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
                backend.addEntry(conn,
                        tagField.getText(),
                        userField.getText(),
                        passField.getPassword());

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
    class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setText("Show");
        }

        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            return this;
        }
    }

    // ===== BUTTON EDITOR =====
    class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private int row;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);

            button = new JButton("Show");
            button.addActionListener(e -> showPassword(row));
        }

        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int col) {
            this.row = row;
            return button;
        }

        public Object getCellEditorValue() {
            return "Show";
        }
    }

    // ===== SALT HANDLING =====
    // A salt is just random data added to a password before key derivation --- prevents Rainbow Table attacks
    private byte[] getOrCreateSalt(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();

        stmt.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value BLOB)");

        PreparedStatement ps = conn.prepareStatement("SELECT value FROM meta WHERE key='salt'");
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return rs.getBytes(1);
        }

        byte[] salt = new byte[16];
        new java.security.SecureRandom().nextBytes(salt);

        PreparedStatement insert = conn.prepareStatement("INSERT INTO meta(key,value) VALUES('salt',?)");
        insert.setBytes(1, salt);
        insert.executeUpdate();

        return salt;
    }
}