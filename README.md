<div align="center"><img width="250" height="250" alt="Image" src="https://github.com/user-attachments/assets/76ae44a2-00a7-453a-8b75-595f184bd7a2" /></div>
<h1 align="center">Java Password Vault</h1>
<h3 align="center">(Java + SQLite + AES-GCM + Post Quantum Resistant)</h3>

<h4 align="center">Keeping secrets safe. Since April 2026</h4>

## Overview

This project is a **secure, minimal password vault** built in Java using Swing for the GUI and SQLite3 for storage. It is designed to demonstrate core security concepts while remaining lightweight and easy to run with only a standard JDK and a single dependency in one neat package (.jar). Post Quantum Resistant using AES-256-GCM according to NIST and other Cybersecurity Experts.

The vault encrypts all stored data using **AES-256 in GCM mode**, with keys derived from a user-provided master password and a salted key derivation function. Sensitive data is only decrypted in memory when explicitly requested by the user, minimizing exposure.

---

## 🧠 Purpose

This project is intended as a **learning-focused implementation** of a password manager, demonstrating real-world cryptographic practices such as authenticated encryption, secure key handling, and safe storage patterns.

---
<div align="center">

<figure>
  <img width="607" height="408" alt="Image" src="https://github.com/user-attachments/assets/b1f83315-dc38-411d-a1a8-30c0f2718d31" />
  <figcaption>Main vault view</figcaption>
</figure>

---

<figure>
  <img width="610" height="239" alt="Image" src="https://github.com/user-attachments/assets/5bd6e4de-cf57-4d64-ad7a-243184abbaa9" />
  <figcaption>What the data looks like stored. All data is not readable without the key.</figcaption>
</figure>

</div>

---

## 🖥️ Features and Design

* **Encryption:** AES-256-GCM (confidentiality + integrity) (Post Quantum Resistant)
* **Key Derivation:** PBKDF2 with random salt (stored in database)
* **Password Handling:** Stored in `char[]`, wiped from memory after use
* **Per-entry IV:** Each username and password uses a unique random IV

* **Master Password Prompt at start-up**
* **Create vault at first start-up**
* **Entry controls:** Add (tag/url, username, password), delete, copy and reveal passwords
* **On-demand Decryption:** Passwords are only decrypted when requested to copy or show
* **No Master Password Storage:** Master password is never saved
* **All data is encrypted:** Using AES256-GCM which is Post Quantum Resistant

* **Cross-platform support (Windows / Linux / macOS)** Java is cross-platform compatible and this project is devoted to keeping it that way.
* **Standalone compiled .jar executable**

* **Shutdown Hook:** Master password is cleared from memory before program is shutdown if NOT by `kill -9` or `Force End`
* **Idle Session Timeout:** App will close if idle for 10 minutes, locking the vault

---

## 💾 Storage

* Database: `vault.db` (SQLite)
* Tables:

  * `vault`: stores encrypted (tag, username, password), and IV
  * `meta`: stores salt, encrypted vault username, and future metadata

Tags, Usernames, and Passwords are stored as encrypted binary blobs.

---

## ⚙️ Requirements

* Java JDK 17+ (tested on newer versions)
* SQLite JDBC driver:

  * `sqlite-jdbc-3.53.0.0.jar`
  * `argon2-jvm-2.12.jar`

No external database or installer required.

---

## ⚠️ Limitations

* Uses PBKDF2 (Argon2 not yet implemented)
* Memory may not be fully cleared, always a risk, but data is always encrypted on disk. (Work around: turn off swap space)(Reboot/Shutdown of your machine clears the memory)
* Will not save you from keyloggers or other kinds of malware but will keep the data safe if the vault is closed.

---

## 🚀 Future Improvements

**[ Major Upgrades ]**
* Argon2 key derivation (More modern but not a rush)
* Multi-User account option (Shared encryption key) (Good for legacy accounts or small business)

**[ New Features ]**
* Changing Master Password
* Search/filter functionality
* Password Generator
* Passphrase Generator
* Password Changing

**[ Big Ticket Items ]**
* Browser Extension for Firefox and Chrome
* Import/Export from/to Bitwarden

**[ New Data Storage ]**
* Passkeys
* Secure Notes
* Credit/Debt card data storage
* Address data storage

---
## 🖥️ Platforms Supported (Tested On)

    ✅ Debian 11+
    ✅ Ubuntu 20.04/22.04+
    ✅ Linux Mint 20+
    ✅ Windows 11



## INSTALL:
1) Download the latest released .deb package files off of github at https://github.com/stormtheory/java-password-vault/releases and install on your system.

          #### Windows/Linux/MacOS ####
          # Download then execute like normal or use Linux command:

          java -jar JavaPasswordVault-*.jar


2) Manual Install without Package Manager, run commands:

    Download the zip file of the code, off of Github. This is found under the [<> Code] button on https://github.com/stormtheory/java-password-vault.

    Extract directory from the zip file. Run the following commands within the directory.

        #/In Folder Requirements
          Backend.java
          GUI.java
          IdleTimeoutManager.java
          lib/sqlite-jdbc-3.53.0.0.jar
          lib/argon2-jvm-2.12.jar
          bin/
          icons/


        # Linux Install or edit code:
            cd java-password-vault
                ./build.sh -br  # Build and Run

                # or

                ./build.sh -r  # Run
            

        # Windows Install or edit code:
            javac -cp ".;sqlite-jdbc-3.53.0.0.jar" -d go *.java
            
              

## RUN:
### run the local App

        # Linux:
            cd java-password-vault
            ./build.sh -r

        # Windows:
            Within the folder run command:
            java -cp "go;sqlite-jdbc-3.53.0.0.jar" GUI

---

# Using Netbeans:
### What a NetBeans user needs to do
    YourProject/
    ├── src/
    │   └── icons/
    │   |    ├── icon_16.png
    │   |    ├── icon_32.png
    │   |    └── icon_256.png
    |   └── GUI.java
    |   └── Backend.java
    |   └── IdleTimeoutManager.java
    └── build/

   1. `File` >> `New Project` >> 
      `Java with ANT` >> `Java Appilcation` >> `NEXT` >>
      Project Name: `JavaVault` (or whatever) >> `Select your locations` >> `Deselect Create Main Class` >> Click `Finish`
            
   2. Drag and drop the files below into your Source Packages under \<default package>:
      `Backend.java`
      `GUI.java`
      `IdleTimeoutManager.java`

   3. Add the needed Libraries
      In NetBeans:
         Right-click `Libraries` >> Click `Add JAR/Folder` >> Select:`sqlite-jdbc-3.53.0.0.jar`
         Right-click `Libraries` >> Click `Add JAR/Folder` >> Select:`argon2-jvm-2.12.jar`

   4. May need to add:
      Add JVM option in NetBeans
        Right-click project (JavaVault)
          `Properties`
          Go to:
          `Run`
          Look for a large box called `[VM Options]`, copy and paste in:
               `--enable-native-access=ALL-UNNAMED`
          If you have a locked down (noexec) /tmp directory you will also need to add:
               `-Djava.io.tmpdir=.`

   5. Click the green Play Button (`Run Project`)
   6. Select GUI as your main class

## Clean and Build:
### one JAR contains everything - run anywhere

            ✔ Works on all platforms
            ✔ No classpath needed
            ✔ No extra files
            
            # FAT JAR
            On Linux run:

            ./build.sh -j


## Database Versions
**[ 0 ]** [Current]
* Beta: |PBKDF2|AES256-GCM|SALT| Testing of new database ideas and expanding, expect to have to rebuild if something changes ina newer version, so keep your older versions until tested.