# 🔐  java-password-vault (Java + SQLite + AES-GCM)

## Overview

This project is a **secure, minimal password vault** built in Java using Swing for the GUI and SQLite for storage. It is designed to demonstrate core security concepts while remaining lightweight and easy to run with only a standard JDK and a single dependency.

The vault encrypts all stored passwords using **AES-256 in GCM mode**, with keys derived from a user-provided master password and a salted key derivation function. Sensitive data is only decrypted in memory when explicitly requested by the user, minimizing exposure.

---

## 🧠 Purpose

This project is intended as a **learning-focused implementation** of a password manager, demonstrating real-world cryptographic practices such as authenticated encryption, secure key handling, and safe storage patterns.

---

## 🔒 Security Design Summary

* **Encryption:** AES-256-GCM (confidentiality + integrity)
* **Key Derivation:** PBKDF2 with random salt (stored in database)
* **Password Handling:** Stored in `char[]`, wiped from memory after use
* **Per-entry IV:** Each username and password uses a unique random IV
* **On-demand Decryption:** Passwords are only decrypted when requested
* **No Master Password Storage:** Master password is never saved

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
* Add new entries (tag, username, password)
* View stored entries (passwords hidden by default)
* Reveal password on demand
* Delete entries with confirmation
* Cross-platform support (Windows / Linux / macOS)

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

---

## 🚀 Future Improvements

* Master password verification (`vault_check`) (Using a username for the vault.)
* Argon2 key derivation
* Auto-lock on inactivity
* Clipboard copy with auto-clear
* Search/filter functionality
* Packaging into standalone executable

---
## 🖥️ Platforms Supported

    ✅ Debian 11+
    ✅ Ubuntu 20.04/22.04+
    ✅ Linux Mint 20+
    ✅ Windows 11



## INSTALL:
1) Download the latest released .deb package files off of github at https://github.com/stormtheory/java-password-vault/releases and install on your system.

          Not supported yet, see manual install.

3) Manual Install without Package Manager, run commands:

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
        1. Add the SQLite driver
            In NetBeans:

            Right-click project
            Properties
            Go to Libraries
            Click Add JAR/Folder
            Select:
                sqlite-jdbc-3.53.0.0.jar
        2. May need to add:
            Add JVM option in NetBeans
                Right-click project
                Properties
                Go to:
                Run
                In VM Options, add:
                    --enable-native-access=ALL-UNNAMED

## Clean and Build:
### one JAR contains everything - run anywhere

            ✔ Works on all platforms
            ✔ No classpath needed
            ✔ No extra files
            
            # FAT JAR
            NetBeans does not do this by default:

            Step 1 — Extract SQLite JAR
                Unzip:
                sqlite-jdbc-3.53.0.0.jar
            Step 2 — Merge into your JAR
                Build your project
                Open your generated JAR (zip tool)
                Copy ALL contents from sqlite-jdbc into it
            Step 3 — Ensure manifest is correct
                Inside your JAR:

                    META-INF/MANIFEST.MF

                    Must include:

                        Main-Class: GUI
                        Step 4 — Run
                        java -jar YourProject.jar