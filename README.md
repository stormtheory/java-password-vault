<div align="center"><img width="250" height="250" alt="Image" src="https://github.com/user-attachments/assets/76ae44a2-00a7-453a-8b75-595f184bd7a2" /></div>
<h1 align="center">Java Password Vault</h1>
<h3 align="center">(Java + SQLite + AES-GCM)</h3>

<h4 align="center">Keeping secrets safe. Since April 2026</h4>

## Overview

This project is a **secure, minimal password vault** built in Java using Swing for the GUI and SQLite for storage. It is designed to demonstrate core security concepts while remaining lightweight and easy to run with only a standard JDK and a single dependency.

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

## 🔒 Security Design Summary

* **Encryption:** AES-256-GCM (confidentiality + integrity)
* **Key Derivation:** PBKDF2 with random salt (stored in database)
* **Password Handling:** Stored in `char[]`, wiped from memory after use
* **Per-entry IV:** Each username and password uses a unique random IV
* **On-demand Decryption:** Passwords are only decrypted when requested
* **No Master Password Storage:** Master password is never saved
* **Shutdown Hook:** Master password is cleared from memory before program is shutdown if NOT by `kill -9` or `Force End`

---

## 💾 Storage

* Database: `vault.db` (SQLite)
* Tables:

  * `vault`: stores encrypted (tag, username, password), and IV
  * `meta`: stores salt, encrypted vault username, and future metadata

Tags, Usernames, and Passwords are stored as encrypted binary blobs.

---

## 🖥️ Features

* Master password prompt on startup
* Create new vault on first run
* Add new entries (tag/url, username, password)
* View stored entries (passwords hidden by default)
* Reveal password on demand
* Delete entries with confirmation
* Cross-platform support (Windows / Linux / macOS)
* Shutdown Hook clears master password at graceful shutdown
* Standalone compiled .jar executable

---

## ⚙️ Requirements

* Java JDK 17+ (tested on newer versions)
* SQLite JDBC driver:

  * `sqlite-jdbc-3.53.0.0.jar`

No external database or installer required.

---

## ⚠️ Limitations

* No auto-lock or session timeout
* Uses PBKDF2 (Argon2 not yet implemented)
* Memory may not be fully cleared, always a risk, but data is always encrypted on disk. (Work around: turn off swap space)(Reboot/Shutdown of your machine clears the memory)

---

## 🚀 Future Improvements

* Argon2 key derivation
* Auto-lock on inactivity
* Search/filter functionality


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

        /In Folder Requirements
          Backend.java
          GUI.java
          sqlite-jdbc-3.53.0.0.jar


        # Linux Install or edit code:
            cd java-password-vault
                javac -cp ".:sqlite-jdbc-3.53.0.0.jar" -d go *.java

                # or

                ./build.sh -br  # Build and Run
            

        # Windows Install or edit code:
            javac -cp ".;sqlite-jdbc-3.53.0.0.jar" -d go *.java
            
              

## RUN:
### run the local App

        # Linux:
            cd java-password-vault
            java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp "go:sqlite-jdbc-3.53.0.0.jar" GUI

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
    └── build/

   1. `File` >> `New Project` >> 
      `Java with ANT` >> `Java Appilcation` >> `NEXT` >>
      Project Name: `JavaVault` (or whatever) >> `Select your locations` >> `Deselect Create Main Class` >> Click `Finish`
            
   2. Drag and drop the files below into your Source Packages under \<default package>:
      `Backend.java`
      `GUI.java`

   3. Add the SQLite driver
      In NetBeans:
         Right-click `Libraries` >> Click `Add JAR/Folder` >> Select:`sqlite-jdbc-3.53.0.0.jar`

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
