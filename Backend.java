import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;

import javax.security.auth.DestroyFailedException;
import javax.swing.JOptionPane;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.sql.*;
import java.util.*;

import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Backend {
    public boolean DEBUG = false; //true or false set to false before production

    // ===== CONFIG =====
    // Strength factors
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int SALT_SIZE = 32;

    private static byte[] User_AES_Key;
    private static byte[] Vault_KEY;
    private static byte[] Vault_Use_Key;
    protected String VaultLevel = "";
    protected String VK_STATUS = "";
    //protected String username = "";
    private static byte[] user_salt;
    protected DatabaseUtilities databaseutilities = new DatabaseUtilities();

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
        System.out.println("masterPassword empty? " + (masterPassword == null || masterPassword.length == 0 || masterPassword[0] == '\0'));
        Security.addProvider(new BouncyCastleProvider());
        int[] params = loadArgon2Params(conn, username);
        user_salt = loadUserSalt(conn, username);
        User_AES_Key = deriveKey(masterPassword, params, username, user_salt, conn);
        VK_STATUS = databaseutilities.Pull_DB_Status(conn, "vk_status");
                                                                
        if (type.equals("m")){
                                                                     
            if (VK_STATUS.equals("gen")){
                                                                    
               Vault_KEY = generateVaultKey();
                                
                // Wrap Vault Key with Argon2 generated key
                byte[] wrappedKey = wrapVaultKey(User_AES_Key);
                // Save the wrapped key
                try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE users SET wrapped_vk = ? WHERE user_id = ?")) {
                    update.setBytes(1, wrappedKey);
                    update.setString(2, username);
                    update.executeUpdate();
                }
                databaseutilities.Update_DB_Status(conn, "vk_status", "true");
                wipeByteArray(wrappedKey);
            } 
            else if (VK_STATUS.equals("true")){
                String sql = "SELECT wrapped_vk FROM users WHERE user_id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                // Extract wrapped key bytes from result
                Cipher cipher = Cipher.getInstance("AESWrapPad");
                cipher.init(Cipher.UNWRAP_MODE, new SecretKeySpec(User_AES_Key, "AES"));
                SecretKey tempKey = (SecretKey) cipher.unwrap(rs.getBytes("wrapped_vk"), "AES", Cipher.SECRET_KEY);
                Vault_KEY = tempKey.getEncoded();
                try { tempKey.destroy(); } catch (DestroyFailedException e) { /* expected */ }
                wipeByteArray(User_AES_Key);
            }
        }

        if (type.equals("m")){
            Vault_Use_Key = Vault_KEY;
        } else {
            Vault_Use_Key = User_AES_Key;
        }

        wipeCharArray(masterPassword);
    }

    protected void changeMasterPass(Connection conn, char[] masterPassword,String username)
        {
                // make new salt
                //param
                //devkey
                // wrap key
                // put back key and new salt in database
        }

    protected static void cleanupWipeDown() throws Exception {
        System.out.println("Cleaning Backend");
        wipeByteArray(User_AES_Key);
        wipeByteArray(Vault_Use_Key);
        wipeByteArray(Vault_KEY);
    }

    // ===== LOAD ARGON2 PARAMETERS FROM DB =====
        // Always derive the key using the parameters the vault was CREATED with
        // Never use hardcoded constants at derive-time - who knows when the parameters may have been upgraded
        private int[] loadArgon2Params(Connection conn, String username) throws Exception {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT argon2_iter, argon2_mem, argon2_para FROM users WHERE user_id = ?"
            );
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            return new int[]{rs.getInt("argon2_iter"), rs.getInt("argon2_mem"), rs.getInt("argon2_para")};
        }

        private byte[] loadUserSalt(Connection conn, String username) throws Exception {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT salt FROM users WHERE user_id = ?"
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
        private byte[] deriveKey(char[] password, int[] params, String username, byte[] u_salt, Connection conn) throws Exception {
            Argon2Advanced argon2 = (Argon2Advanced) Argon2Factory.createAdvanced(Argon2Types.ARGON2id);

            // rawHash() returns raw bytes — exactly what AES needs as a key
            byte[] keyBytes = argon2.rawHash(params[0], params[1], params[2], password, u_salt);

            // Check to make sure key is 32 bytes, which is what AES-256 uses for a key
            if (keyBytes.length < 32) {
                throw new SecurityException("Derived key too short for AES-256");
            }

            // Return raw bytes directly — caller must wipe with Arrays.fill after use
            return keyBytes;
        }

        private static byte[] generateVaultKey() throws Exception {
            // Explicitly request BouncyCastle's SecureRandom — avoids JDK default provider
            // Requires BouncyCastleProvider to be registered at app startup
            SecureRandom random = SecureRandom.getInstance("DEFAULT", "BC");

            // Generate 256-bit (32 byte) AES key directly into a byte[]
            // No SecretKey wrapper — byte[] is fully wipeable with Arrays.fill() after use
            byte[] keyBytes = new byte[32];
            random.nextBytes(keyBytes);

            return keyBytes;
        }


    // ===== ENCRYPT ===== using AES256-GCM which AES is like a lock 🔒 and GCM is the tamper seal 🧾
    // 🔒 will encrypt whatever data you feed into it, then return the ecypted value
    // IV is a random value used during encryption so that the same input doesn’t produce the same hash output
    // Generated for us whenever new data cycle/set is saved
            private byte[] encryptData(char[] plaintext, byte[] iv) throws Exception {
            // Convert char[] to bytes so we can wipe after use
            byte[] data = new String(plaintext).getBytes(StandardCharsets.UTF_8);
            // KeyParameter holds raw key bytes — wipeable unlike SecretKey
            KeyParameter keyParam = new KeyParameter(Vault_Use_Key);
            // GCM mode — authenticated encryption, 128-bit tag
            GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
            AEADParameters params = new AEADParameters(keyParam, GCM_TAG_LENGTH, iv);
            cipher.init(true, params); // true = encrypt
            // Allocate output buffer and process
            byte[] encrypted_data = new byte[cipher.getOutputSize(data.length)];
            int len = cipher.processBytes(data, 0, data.length, encrypted_data, 0);
            cipher.doFinal(encrypted_data, len);
            // Wipe sensitive byte arrays immediately after use
            wipeByteArray(data);
            Arrays.fill(keyParam.getKey(), (byte) 0);
            return encrypted_data;
        }

    // ===== DECRYPT (ON DEMAND ONLY) =====
            protected char[] decryptData(byte[] encrypted_data, byte[] iv) throws Exception {

            // KeyParameter holds raw key bytes — wipeable unlike SecretKey
            KeyParameter keyParam = new KeyParameter(Vault_Use_Key);

            // GCM mode — authenticated decryption, 128-bit tag
            GCMModeCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
            AEADParameters params = new AEADParameters(keyParam, GCM_TAG_LENGTH, iv);
            cipher.init(false, params); // false = decrypt

            // Allocate output buffer and process
            byte[] decrypted_charset = new byte[cipher.getOutputSize(encrypted_data.length)];
            int len = cipher.processBytes(encrypted_data, 0, encrypted_data.length, decrypted_charset, 0);
            cipher.doFinal(decrypted_charset, len);

            // Convert to char[] so we can wipe the byte[] after
            char[] plaintext = new String(decrypted_charset, StandardCharsets.UTF_8).toCharArray();

            // Wipe sensitive byte arrays immediately after use
            wipeByteArray(decrypted_charset);
            Arrays.fill(keyParam.getKey(), (byte) 0);

            return plaintext;
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
    protected void addEntry(Connection conn, char[] tag, char[] username, char[] password, char [] notes, String type) throws Exception {
        byte[] iv = generateIV();
        
        byte[] encrypted_tag = encryptData(tag, iv);
        byte[] encrypted_username = encryptData(username, iv);
        byte[] encrypted_pass = encryptData(password, iv);
        byte[] encrypted_notes = encryptData(notes, iv);
        
        String sql = "INSERT INTO vault(tag, username, password, notes, iv) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);

        stmt.setBytes(1, encrypted_tag);
        stmt.setBytes(2, encrypted_username);
        stmt.setBytes(3, encrypted_pass);
        stmt.setBytes(4, encrypted_notes);
        stmt.setBytes(5, iv);

        stmt.executeUpdate();

        wipeCharArray(username);
        wipeCharArray(tag);
        wipeCharArray(password);
        wipeCharArray(notes);
        
        wipeByteArray(encrypted_username);
        wipeByteArray(encrypted_tag);
        wipeByteArray(encrypted_pass);
        wipeByteArray(encrypted_notes);
    }

    private byte[] wrapVaultKey(byte[] new_User_AES_Key) throws Exception {
        // Wrap Vault Key with Argon2 generated key
            Cipher cipher = Cipher.getInstance("AESWrapPad");
            cipher.init(Cipher.WRAP_MODE, new SecretKeySpec(new_User_AES_Key, "AES"));
            return cipher.wrap(new SecretKeySpec(Vault_KEY, "AES"));//// Has to Vault_Key becuase of where the Logic is in FireUp
    }

    // ===== User ADD =====  ---- Has to happen at some point?
    protected void useraddEntry(Connection conn, String newUsername, char[] newPassword) throws Exception {
        
        ////////////////// Pull Level

         // --- Or select dynamically based on context (e.g. user tier, data sensitivity) --
        VaultLevel = "HIGH";
        Argon2Profile.Profile profile = switch (VaultLevel) {
            case "MINIMUM"  -> Argon2Profile.MINIMUM;
            case "BALANCED"   -> Argon2Profile.BALANCED;
            case "HIGH"     -> Argon2Profile.HIGH;
            case "PARANOID"    -> Argon2Profile.PARANOID;
            default -> throw new IllegalArgumentException("Unknown security profile: " + VaultLevel);
        };
            byte[] new_salt = new byte[SALT_SIZE];
            new java.security.SecureRandom().nextBytes(new_salt);
        
            int[] params = {profile.iterations(), profile.memoryKb(), profile.parallelism()};
            byte[] new_User_AES_Key = deriveKey(newPassword, params, newUsername, new_salt, conn);           
            byte[] newWrappedKey = wrapVaultKey(new_User_AES_Key);

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO users(user_id,role,wrapped_vk,salt,argon2_iter,argon2_mem,argon2_para) VALUES(?,?,?,?,?,?,?)")) {
                insert.setString(1, newUsername);
                insert.setString(2, "memeber");
                insert.setBytes(3, newWrappedKey);
                insert.setBytes(4, new_salt);
                insert.setInt(5, profile.iterations());
                insert.setInt(6, profile.memoryKb());
                insert.setInt(7, profile.parallelism());
                insert.addBatch();

                insert.executeBatch();
            }

        wipeByteArray(new_User_AES_Key);
        wipeByteArray(newWrappedKey);
        wipeCharArray(newPassword);
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

                // Database vault security level identifier
                insert.setString(1, "vault_level");
                insert.setString(2, VaultLevel);
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