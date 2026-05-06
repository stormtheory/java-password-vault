import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.KeyGenerator;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.sql.*;
import java.util.*;

import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;

public class Backend {
    public boolean DEBUG = false; //true or false set to false before production

    // ===== CONFIG =====
    // Strength factors
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_SIZE = 32;

    private static SecretKey User_AES_Key;
    private static SecretKey Vault_KEY;
    private static SecretKey Vault_Use_Key;
    protected String VaultLevel = "";
    protected String VK_STATUS = "";
    //protected String username = "";
    private static byte[] user_salt;

    // ===== DATA CLASS =====
    // If you build it they will come...
    protected static class Credential {
        protected int id;
        protected String tag;
        protected String username;
        protected byte[] encryptedPassword;
        protected byte[] iv;
    }

    // ===== FIRE THEM UP (INIT) ===== 
    // A salt is just random data added to a password before key derivation --- prevents Rainbow Table attacks
    protected void GetFiredUp(char[] masterPassword, byte[] vault_salt, Connection conn, String username, String type) throws Exception {
        System.out.println(username);
        int[] params = loadArgon2Params(conn, username);
        User_AES_Key = deriveKey(masterPassword, params, username, conn);
        VK_STATUS = Pull_DB_Status(conn, "vk_status");
        if (type.equals("m")){
            if (VK_STATUS.equals("gen")){
               Vault_KEY = generateVaultKey();

                // Wrap Vault Key with Argon2 generated key
                Cipher cipher = Cipher.getInstance("AESWrapPad");
                byte[] userkeyBytes = User_AES_Key.getEncoded();
                byte[] vkBytes = Vault_KEY.getEncoded();
                cipher.init(Cipher.WRAP_MODE, new SecretKeySpec(userkeyBytes, "AES"));
                byte[] wrappedKey = cipher.wrap(new SecretKeySpec(vkBytes, "AES"));
               
                // Save the wrapped key
                try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE users SET wrapped_vk = ? WHERE user_id = ?")) {
                    update.setBytes(1, wrappedKey);
                    update.setString(2, username);
                    update.executeUpdate();
                }
                Update_DB_Status(conn, "vk_status", "true");
                wipeByteArray(wrappedKey);
                wipeByteArray(userkeyBytes);
                wipeByteArray(vkBytes);
            } 
            else if (VK_STATUS.equals("true")){
                String sql = "SELECT wrapped_vk FROM users WHERE user_id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                // Extract wrapped key bytes from result
                byte[] userkeyBytes = User_AES_Key.getEncoded();
                Cipher cipher = Cipher.getInstance("AESWrapPad");
                cipher.init(Cipher.UNWRAP_MODE, new SecretKeySpec(userkeyBytes, "AES"));
                Vault_KEY = (SecretKey) cipher.unwrap(rs.getBytes("wrapped_vk"), "AES", Cipher.SECRET_KEY);
                wipeByteArray(userkeyBytes);
            }
        }

        if (type.equals("m")){
            Vault_Use_Key = Vault_KEY;
        } else {
            Vault_Use_Key = User_AES_Key;
        }

        wipeCharArray(masterPassword);
    }

    protected static void cleanupWipeDown() throws Exception {
        try {if (User_AES_Key != null) User_AES_Key.destroy();}catch (Exception t) { System.out.println(t); }
        try {if (Vault_Use_Key != null) Vault_Use_Key.destroy();}catch (Exception g) { System.out.println(g); }
        try {if (Vault_KEY != null) Vault_KEY.destroy();}catch (Exception r) { System.out.println(r); }
    }

    // ===== LOAD ARGON2 PARAMETERS FROM DB =====
        // Always derive the key using the parameters the vault was CREATED with
        // Never use hardcoded constants at derive-time - who knows when the parameters may have been upgraded
        private int[] loadArgon2Params(Connection conn, String username) throws Exception {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT argon2_iter, argon2_mem, argon2_para, salt FROM users WHERE user_id = ?"
            );
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
                user_salt = rs.getBytes("salt");
            return new int[]{rs.getInt("argon2_iter"), rs.getInt("argon2_mem"), rs.getInt("argon2_para")};
        }

        private byte[] loadUserSalt(Connection conn, String username) throws Exception {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT argon2_iter, argon2_mem, argon2_para, salt FROM users WHERE user_id = ?"
            );
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
                return rs.getBytes("salt");
            }

    // ===== KEY DERIVATION ===== Argon2id replaced PBKDF2 entirely ===== One of the most important parts!!!! 
        // Key derivation turns a human readable password into a strong cryptographic key
        // Argon2id is memory-hard, makes brute force expensive even with GPUs
        // Argon2id hybrid variant, resistant to both GPU and side-channel attacks
        // Output raw bytes are used directly as the AES key — no PBKDF2 involvement anymore!
        private SecretKey deriveKey(char[] password, int[] params, String username, Connection conn ) throws Exception {            
            Argon2Advanced argon2 = (Argon2Advanced) Argon2Factory.createAdvanced(Argon2Types.ARGON2id);

            // rawHash() returns raw bytes — exactly what AES needs as a key
            byte[] keyBytes = argon2.rawHash(params[0], params[1], params[2], password, loadUserSalt(conn, username));

            // Check to make sure key is 32 bytes, which is what AES-256 uses for a key
            if (keyBytes.length < 32) {
                throw new SecurityException("Derived key too short for AES-256");
            }

            // Wrap raw bytes as AES key
            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            
            // Wipe raw key bytes from memory immediately after wrapping
            wipeByteArray(keyBytes);

            return key;
        }

        private static SecretKey generateVaultKey() throws Exception {
            // Initialize AES key generator with 256-bit key size
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, new java.security.SecureRandom());
            // Generate raw AES-256 vault key
            return keyGen.generateKey();   
        }


    // ===== ENCRYPT ===== using AES256-GCM which AES is like a lock 🔒 and GCM is the tamper seal 🧾
    // 🔒 will encrypt whatever data you feed into it, then return the ecypted value
    // IV is a random value used during encryption so that the same input doesn’t produce the same hash output
    // Generated for us whenever new data cycle/set is saved
    private byte[] encryptData(char[] plaintext, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, Vault_Use_Key, spec);

        byte[] data = new String(plaintext).getBytes(StandardCharsets.UTF_8);
        byte[] encrypted_data = cipher.doFinal(data);

        wipeByteArray(data);
        return encrypted_data;
    }

    // ===== DECRYPT (ON DEMAND ONLY) =====
    protected char[] decryptData(byte[] encrypted_data, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, Vault_Use_Key, spec);

        byte[] decrypted_charset = cipher.doFinal(encrypted_data);
        char[] plaintext = new String(decrypted_charset, StandardCharsets.UTF_8).toCharArray();

        wipeByteArray(decrypted_charset);
        return plaintext;
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


    // ===== DB LOAD ===== This just load the database to an Arraylist and decrypt Tag and Usernames
    protected List<Credential> loadAll(Connection conn) throws Exception {
        List<Credential> list = new ArrayList<>();

        String sql = "SELECT id, tag, username, password, iv FROM vault";
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
            Credential c = new Credential();
            c.id = rs.getInt("id");
            c.iv = rs.getBytes("iv");
            c.tag = new String(decryptData(rs.getBytes("tag"), c.iv));
            c.username = new String(decryptData(rs.getBytes("username"), c.iv));
            c.encryptedPassword = rs.getBytes("password");
            list.add(c);
        }

        return list;
    }

    // ===== ADD ENTRY =====  ---- Has to happen at some point?
    protected void addEntry(Connection conn, char[] tag, char[] username, char[] password, String type) throws Exception {
        byte[] iv = generateIV();
        
        byte[] encrypted_tag = encryptData(tag, iv);
        byte[] encrypted_username = encryptData(username, iv);
        byte[] encrypted_pass = encryptData(password, iv);
        
        String sql = "INSERT INTO vault(tag, username, password, iv) VALUES (?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setBytes(1, encrypted_tag);
        stmt.setBytes(2, encrypted_username);
        stmt.setBytes(3, encrypted_pass);
        stmt.setBytes(4, iv);

        stmt.executeUpdate();

        wipeCharArray(username);
        wipeByteArray(encrypted_username);

        wipeCharArray(tag);
        wipeByteArray(encrypted_tag);

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
    // Will need to update both Tag and Username as well
    protected void updatePassword(Connection conn, int id, char[] newPassword) throws Exception {
        byte[] iv = generateIV();
        byte[] encrypted_pass = encryptData(newPassword, iv);

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
    // Will need to update both Tag and Password as well
    protected void updateUsername(Connection conn, int id, char[] newUsername) throws Exception {
        byte[] iv = generateIV();
        byte[] encrypted_pass = encryptData(newUsername, iv);

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


    protected void BuildDatabase(Connection conn, String username, String version, String type, String VaultLevel) throws Exception {
        Statement stmt = conn.createStatement();

    // --- Or select dynamically based on context (e.g. user tier, data sensitivity) ---
    Argon2Profile.Profile profile = switch (VaultLevel) {
        case "MINIMUM"  -> Argon2Profile.MINIMUM;
        case "BALANCED"   -> Argon2Profile.BALANCED;
        case "HIGH"     -> Argon2Profile.HIGH;
        case "PARANOID"    -> Argon2Profile.PARANOID;
        default -> throw new IllegalArgumentException("Unknown security profile: " + VaultLevel);
    };

        //stmt.execute("""
        //    PRAGMA journal_mode=WAL;
        //    PRAGMA foreign_keys=ON;
        //""");

        // Vault table
        stmt.execute("""
            CREATE TABLE vault (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type text,
                tag BLOB,
                username BLOB,
                password BLOB,
                notes BLOB,
                data BLOB,
                iv BLOB
            )
        """);

        // Meta table (for vault_salt)
        stmt.execute("""
            CREATE TABLE meta (
                key TEXT PRIMARY KEY,
                Bvalue BLOB,
                Tvalue Text
            )
        """);

        // Users table
        stmt.execute("""
            CREATE TABLE users (
                user_id TEXT PRIMARY KEY,
                role TEXT,
                wrapped_vk BLOB,
                salt BLOB,
                argon2_iter INTEGER,
                argon2_mem INTEGER,
                argon2_para INTEGER,
                created_at INTERGER,
                last_login INTEGER
            )
        """);

            try (PreparedStatement insert = conn.prepareStatement(
            "INSERT INTO meta(key,Tvalue) VALUES(?,?)")) {

                // Schema version — for future migrations
                insert.setString(1, "version");
                insert.setString(2, version);
                insert.addBatch();

                // Database type identifier
                insert.setString(1, "type");
                insert.setString(2, type);
                insert.addBatch();

                if (type.equals("m")) {
                    // Database VK has been generatored True/False/Gen identifier
                    VK_STATUS = "gen";
                    insert.setString(1, "vk_status");
                    insert.setString(2, "gen");
                    insert.addBatch();
                }

                insert.executeBatch();
            }


            user_salt = new byte[SALT_SIZE];
            new java.security.SecureRandom().nextBytes(user_salt);

        if (type.equals("m")) {
            try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO users(user_id,role,salt,argon2_iter,argon2_mem,argon2_para) VALUES(?,?,?,?,?,?)")) {
                insert.setString(1, username);
                insert.setString(2, "admin");
                insert.setBytes(3, user_salt);
                insert.setInt(4, profile.iterations());
                insert.setInt(5, profile.memoryKb());
                insert.setInt(6, profile.parallelism());
                insert.addBatch();

                insert.executeBatch();
            }
        } else if (type.equals("s")){
            try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO users(user_id,salt,argon2_iter,argon2_mem,argon2_para) VALUES(?,?,?,?,?)")) {
                insert.setString(1, username);
                insert.setBytes(2, user_salt);
                insert.setInt(3, profile.iterations());
                insert.setInt(4, profile.memoryKb());
                insert.setInt(5, profile.parallelism());
                insert.addBatch();

                insert.executeBatch();
            }
        }
    }

        // ===== VAULT SALT HANDLING =====
        // A salt is just random data added to a password before key derivation --- prevents Rainbow Table attacks
        protected byte[] getOrCreateVaultSalt(Connection conn) throws Exception {
            Statement stmt = conn.createStatement();

            stmt.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value BLOB)");

            PreparedStatement ps = conn.prepareStatement("SELECT Bvalue FROM meta WHERE key='vault_salt'");
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getBytes(1);
            }
            
            byte[] vault_salt = new byte[SALT_SIZE];
            new java.security.SecureRandom().nextBytes(vault_salt);

            try (PreparedStatement insert = conn.prepareStatement(
            "INSERT INTO meta(key,Bvalue) VALUES(?,?)")) {
                // Salt — stored as bytes
                insert.setString(1, "vault_salt");
                insert.setBytes(2, vault_salt);
                insert.addBatch(); 
                insert.executeBatch();
            }
            return vault_salt;
        }
}