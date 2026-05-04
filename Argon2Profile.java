// Select a profile based on your threat model and hardware budget
public final class Argon2Profile {

    private Argon2Profile() {}

    // Common interface so profiles can be passed around polymorphically (magically)
    public interface Profile {
        int iterations();
        int memoryKb();
        int parallelism();
        int hashLength();
    }

    // --- OWASP 2023 minimum — suitable for low-risk, high-throughput scenarios ---
    public static final Profile MINIMUM = new Profile() {
        public int iterations()  { return 2;     }
        public int memoryKb()    { return 19456; } // 19 MB
        public int parallelism() { return 1;     }
        public int hashLength()  { return 32;    }
    };

    // --- RFC 9106 second recommended choice — balanced for most applications ---
    public static final Profile MEDIUM = new Profile() {
        public int iterations()  { return 3;     }
        public int memoryKb()    { return 65536; } // 64 MB
        public int parallelism() { return 4;     }
        public int hashLength()  { return 32;    }
    };

    // --- RFC 9106 first recommended choice — high-value credential storage ---
    public static final Profile HIGH = new Profile() {
        public int iterations()  { return 4;       }
        public int memoryKb()    { return 1048576; } // 1 GB
        public int parallelism() { return 4;       }
        public int hashLength()  { return 32;      }
    };

    // --- Beyond RFC 9106 — exceeds vault-grade, secrets manager, recovery codes, or master-key derivation ---
    public static final Profile PARANOID = new Profile() {
        public int iterations()  { return 6;       }
        public int memoryKb()    { return 2097152; } // 2 GB —  RFC 9106 for vault-grade security
        public int parallelism() { return 8;       }
        public int hashLength()  { return 64;      } // Extended hash for vault-grade outputs
    };
}