import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.*;

public class Backend {
    public boolean DEBUG = false; //true or false set to false before production

    // ===== CONFIG =====
    // Strength factors
    private static final int KEY_SIZE = 256;
    private static final int ITERATIONS = 65536;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private SecretKey aesKey;

    // ===== DATA CLASS =====
    // If you build it they will come...
    protected static class Credential {
        protected int id;
        protected String tag;
        protected String username;
        protected byte[] encryptedUsername;
        protected byte[] encryptedPassword;
        protected byte[] iv;
    }

    // ===== INIT (derive key once per session) ===== 
    // A salt is just random data added to a password before key derivation --- prevents Rainbow Table attacks
    protected void initialize(char[] masterPassword, byte[] salt) throws Exception {
        this.aesKey = deriveKey(masterPassword, salt);
        if (DEBUG) {
            System.out.println("[INIT] PASS: " + masterPassword.length * 8 + " | Salt: " + salt.length * 8);
            System.out.println("[INIT] AESkey: " + this.aesKey);
            System.out.println("[INIT] AESkey initialized: " + (this.aesKey != null) + " | algorithm: " + this.aesKey.getAlgorithm() + " | size: " + (this.aesKey.getEncoded().length * 8) + " bits");}
        wipeCharArray(masterPassword);
        if (DEBUG) {System.out.println("[INIT] PASS: Wiped");}
        
    }

    // ===== KEY DERIVATION ===== One of the most important parts!!!! 
    // Key derivation turns a human readable password into a strong cryptographic key
    private SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_SIZE);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    // ===== ENCRYPT ===== using AES256-GCM which AES is like a lock 🔒 and GCM is the tamper seal 🧾
    // 🔒 will encrypt whatever data you feed into it, then return the ecypted value
    // IV is a random value used during encryption so that the same input doesn’t produce the same hash output
    // Generated for us whenever new data cycle/set is saved
    private byte[] encrypt(char[] password, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);

        byte[] data = new String(password).getBytes(StandardCharsets.UTF_8);
        byte[] encrypted_data = cipher.doFinal(data);

        wipeByteArray(data);
        return encrypted_data;
    }

    // ===== DECRYPT (ON DEMAND ONLY) =====
    protected char[] decryptData(byte[] encrypted, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);

        byte[] decrypted = cipher.doFinal(encrypted);
        char[] result = new String(decrypted, StandardCharsets.UTF_8).toCharArray();

        wipeByteArray(decrypted);
        return result;
    }

    // ===== DB LOAD (NO DECRYPTION HERE) ===== This just load the database to an Arraylist nothing is decrypted
    protected List<Credential> loadAll(Connection conn) throws Exception {
        List<Credential> list = new ArrayList<>();

        String sql = "SELECT id, tag, username, password, iv FROM vault";
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
            Credential c = new Credential();
            c.id = rs.getInt("id");
            c.tag = rs.getString("tag");
            //c.username = rs.getString("username");
            c.iv = rs.getBytes("iv");
            c.username = new String(decryptData(rs.getBytes("username"), c.iv));
            c.encryptedPassword = rs.getBytes("password");
            c.iv = rs.getBytes("iv");

            list.add(c);
        }

        return list;
    }

    // ===== ADD ENTRY =====  ---- Has to happen at some point?
    protected void addEntry(Connection conn, String tag, char[] username, char[] password) throws Exception {
        byte[] iv = generateIV();
        byte[] encrypted_username = encrypt(username, iv);
        byte[] encrypted_pass = encrypt(password, iv);
        
        String sql = "INSERT INTO vault(tag, username, password, iv) VALUES (?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setString(1, tag);
        stmt.setBytes(2, encrypted_username);
        stmt.setBytes(3, encrypted_pass);
        stmt.setBytes(4, iv);

        stmt.executeUpdate();

        wipeByteArray(encrypted_username);

        wipeCharArray(password);
        wipeByteArray(encrypted_pass);
    }

    // ===== DELETE ENTRY ===== ----- Yup
    protected void deleteEntry(Connection conn, int id) throws Exception {
        String sql = "DELETE FROM vault WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, id);
        stmt.executeUpdate();
    }

    // ===== UPDATE PASSWORD ===== --- I think this is obvious....
    // per item password update
    protected void updatePassword(Connection conn, int id, char[] newPassword) throws Exception {
        byte[] iv = generateIV();
        byte[] encrypted_pass = encrypt(newPassword, iv);

        String sql = "UPDATE vault SET password = ?, iv = ? WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setBytes(1, encrypted_pass);
        stmt.setBytes(2, iv);
        stmt.setInt(3, id);

        stmt.executeUpdate();

        wipeCharArray(newPassword);
        wipeByteArray(encrypted_pass);
    }

    // ===== UPDATE USERNAME ===== --- I think this is obvious....
    // per item username update
    protected void updateUsername(Connection conn, int id, char[] newUsername) throws Exception {
        byte[] iv = generateIV();
        byte[] encrypted_pass = encrypt(newUsername, iv);

        String sql = "UPDATE vault SET username = ?, iv = ? WHERE id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setBytes(1, encrypted_pass);
        stmt.setBytes(2, iv);
        stmt.setInt(3, id);

        stmt.executeUpdate();

        wipeCharArray(newUsername);
        wipeByteArray(encrypted_pass);
    }

    // ===== IV ===== Initialization Vector (IV)
    // a random value used during encryption so that the same input doesn’t produce the same hash output
    // This runs/generates for us whenever new data set/cycle is saved
    private byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    // ===== MEMORY CLEANUP FUNCTIONS (METHODS) =====
    // destroy the keys or plaintext passowrds and data
    protected static void wipeCharArray(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }

    protected static void wipeByteArray(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
}