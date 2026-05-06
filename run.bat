@echo off
:: ####################################################################
:: ##
:: ##              WINDOWS (7 / 10 / 11 / Server 2008+)
:: ##             Script is to make building, launching,
:: ##      and running easier with command line (CLI) arguments
:: ##
:: ##               With Love, Stormtheory
:: ##
:: ####################################################################
:: https://central.sonatype.com/artifact/de.mkammerer/argon2-jvm/versions
  :: https://repo1.maven.org/maven2/de/mkammerer/argon2-jvm/
  :: https://repo1.maven.org/maven2/de/mkammerer/argon2-jvm-nolibs/
  :: https://repo1.maven.org/maven2/net/java/dev/jna/jna/
:: https://github.com/xerial/sqlite-jdbc/releases

:: ── Dependency filenames (update versions here only) ─────────────────
set ARGON2_LIB=argon2-jvm-2.12.jar
set ARGON2_NOLIB=argon2-jvm-nolibs-2.12.jar
set JNA_LIB=jna-5.18.1.jar
set BOUNCY_HOUSE_LIB=bcprov-jdk18on-1.84.jar
set SQLITE_LIB=sqlite-jdbc-3.53.0.0.jar
set JAR_FILENAME=JavaPasswordVault.jar


:: ── Change to the directory containing this script ───────────────────
:: Equivalent to bash's: cd "$(dirname "$0")"
:: This ensures bin\, lib\, and *.java are found correctly regardless of
:: where the script is launched from (Desktop shortcut, Explorer, CLI).
cd /d "%~dp0"

:: ── Detect double-click vs CLI launch ────────────────────────────────
:: When double-clicked from Explorer there is no existing console session.
:: We detect this by checking if the script filename appears in CMDCMDLINE,
:: which Explorer sets when invoking a .bat directly. The practical effect:
:: pause on any error exit so the window stays visible long enough to read.
set DOUBLE_CLICKED=false
echo %CMDCMDLINE% | find /i "%~nx0" >nul 2>&1
if %errorlevel% == 0 set DOUBLE_CLICKED=true

:: ── Safety: refuse to run as a built-in SYSTEM/Administrator account ─
:: Windows has no direct uid=0 equivalent, but we can detect elevation.
:: whoami /groups contains "S-1-16-12288" for High integrity (admin).
:: Note: a standard elevated user will also be blocked; this mirrors
:: the Linux "no root" guard as closely as Windows allows.
whoami /groups | find "S-1-16-12288" >nul 2>&1
if %errorlevel% == 0 (
    echo Not safe to run as Administrator... exiting...
    call :error_exit
)

:: ── Default flag values ──────────────────────────────────────────────
:: All flags start false; parsed flags set them to true.
set DOWNLOADS=false
set DO_BUILD=false
set DO_RUN=false
set DO_TAR=false
set DO_JAR=false
set HELP=true

:: ── Argument parser ──────────────────────────────────────────────────
:: Windows batch has no getopts; we loop through %* manually.
:: Each token that starts with '-' is split into individual characters.
:: ====================================================================
:: FUNCTION: error_exit
::   Centralised error exit. When the script was double-clicked from
::   Explorer, pauses before closing so the user can read the message.
::   Usage: call :error_exit
:: ====================================================================
:error_exit
if "%DOUBLE_CLICKED%"=="true" (
    echo.
    pause
)
exit /b 1

:parse_args
if "%~1"=="" goto end_parse

:: Strip the leading dash and iterate each character as a flag
set "arg=%~1"
:: Only process tokens that begin with a dash
if "%arg:~0,1%"=="-" (
    :: Walk through each character after the dash
    set "flags=%arg:~1%"
    call :parse_flags "%flags%"
) else (
    echo Unknown argument: %1 >&2
    call :show_help
    call :error_exit
)
shift
goto parse_args
:end_parse

:: ── Dispatch based on parsed flags ──────────────────────────────────
if "%DO_JAR%"=="true" (
    call :JAR
    exit /b 0
)

if "%DO_BUILD%"=="true" call :BUILD

if "%DO_TAR%"=="true" call :TAR_UP

:: ── Default behaviour when no flags were passed ───────────────────
:: Goal: the user should always be able to just run this script and
:: have things work. The build should happen exactly once — automatically
:: on first run — and be skipped on every subsequent run unless the
:: user explicitly requests a rebuild with -b or -i.
::
:: Logic:
::   bin\*.class exists  →  skip build, launch immediately
::   bin\*.class missing →  first-time setup: build then launch
::
:: To force a rebuild at any time, pass -b or -br explicitly.
if "%HELP%"=="true" (
    if exist bin\*.class (
        :: Already built — just launch
        echo [Auto] Classes found — launching program...
        call :RUN
    ) else (
        :: First run: no classes yet — build once, then launch
        echo [Auto] First run — building before launch...
        call :BUILD
        echo [Auto] Build complete — launching program...
        call :RUN
    )
    exit /b 0
)

if "%DO_RUN%"=="true" call :RUN

exit /b 0

:: ====================================================================
:: FUNCTION: parse_flags
::   Iterates each character in the passed flag string and sets booleans.
::   Called once per CLI token (e.g. "-br" sets BUILD and RUN).
:: ====================================================================
:parse_flags
set "str=%~1"
:flag_loop
if "%str%"=="" goto :eof
set "char=%str:~0,1%"
set "str=%str:~1%"

if "%char%"=="d" (
    set DO_TAR=true
    set DOWNLOADS=true
    set HELP=false
)
if "%char%"=="i" (
    set DO_BUILD=true
    set HELP=false
)
if "%char%"=="b" (
    set DO_BUILD=true
    set HELP=false
)
if "%char%"=="r" (
    set DO_RUN=true
    set HELP=false
)
if "%char%"=="j" (
    set DO_JAR=true
    set HELP=false
)
if "%char%"=="h" (
    call :show_help
    exit /b 0
)
goto flag_loop

:: ====================================================================
:: FUNCTION: show_help
::   Prints usage information (mirrors the Linux EOF heredoc block).
:: ====================================================================
:show_help
echo.
echo Usage: %~nx0 [OPTIONS]
echo.
echo   (no args)      First run: auto-builds then launches the program.
echo                  Subsequent runs: skips build and launches directly.
echo                  Use -b or -i to force a rebuild at any time.
echo Options:
echo   -d             Copy the zip to the Downloads directory
echo   -i             Force rebuild
echo   -b             Force rebuild
echo   -r             Run only (skips build even if bin\ is empty)
echo   -j             Create fat Jar file
echo   -h             Show this help message
echo.
echo Examples:
echo   %~nx0           -- smart default: build once, then just run
echo   %~nx0 -b        -- force rebuild only
echo   %~nx0 -br       -- force rebuild then run
echo   %~nx0 -r        -- run only (no build check)
echo.
goto :eof

:: ====================================================================
:: FUNCTION: BUILD
::   Cleans the bin directory and recompiles all .java sources.
::   Classpath uses semicolons on Windows (colons on Linux/Mac).
:: ====================================================================
:BUILD
:: Clean old class files before recompile
if exist bin\* del /q bin\*

echo javac -cp ".;lib\%SQLITE_LIB%;lib\%ARGON2_LIB%;lib\%ARGON2_NOLIB%;lib\%BOUNCY_HOUSE_LIB%;lib\%JNA_LIB%;bin" -d bin *.java
javac -cp ".;lib\%SQLITE_LIB%;lib\%ARGON2_LIB%;lib\%ARGON2_NOLIB%;lib\%BOUNCY_HOUSE_LIB%;lib\%JNA_LIB%;bin" -d bin *.java
goto :eof

:: ====================================================================
:: FUNCTION: RUN
::   Launches the compiled GUI using the same JVM flags as Linux.
::   --enable-native-access is required by Argon2's JNA bridge.
::   -Dorg.sqlite.tmpdir=. keeps SQLite temp files local (portable).
:: ====================================================================
:RUN
echo java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp ".;lib\%SQLITE_LIB%;lib\%ARGON2_LIB%;lib\%ARGON2_NOLIB%;lib\%BOUNCY_HOUSE_LIB%;lib\%JNA_LIB%;bin" GUI
java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp ".;lib\%SQLITE_LIB%;lib\%ARGON2_LIB%;lib\%ARGON2_NOLIB%;lib\%BOUNCY_HOUSE_LIB%;lib\%JNA_LIB%;bin" GUI
goto :eof

:: ====================================================================
:: FUNCTION: JAR
::   Builds a fat (uber) jar containing all dependency classes so the
::   app can be distributed as a single executable .jar file.
::   Steps:
::     1. Clean old artifacts
::     2. Compile sources (calls BUILD)
::     3. Explode dependency jars into a staging directory
::     4. Write a manifest pointing at the GUI entry class
::     5. Re-package everything into the final fat jar
:: ====================================================================
:JAR
:: Step 1 – clean old build artifacts
if exist bin\* del /q bin\*
if exist %JAR_FILENAME% del /q %JAR_FILENAME%
if exist fatjar rmdir /s /q fatjar

:: Step 2 – compile (uses BUILD subroutine above)
call :BUILD

:: Step 3 – explode dependency jars into staging directory
mkdir fatjar
:: xcopy /e /i copies class tree from bin into fatjar
xcopy /e /i bin fatjar >nul

:: Explode only the jars bundled in the fat jar (no JNA native libs needed
:: at explode time; they load dynamically via argon2-jvm at runtime)
cd fatjar
jar xf ..\lib\%SQLITE_LIB%
jar xf ..\lib\%ARGON2_LIB%
jar xf ..\lib\%ARGON2_NOLIB%
jar xf ..\lib\%JNA_LIB%
jar xf ..\lib\%BOUNCY_HOUSE_LIB%
cd ..

:: Step 4 – write the manifest (trailing newline required by jar spec)
mkdir fatjar\META-INF
(
    echo Manifest-Version: 1.0
    echo Main-Class: GUI
    echo.
) > fatjar\META-INF\MANIFEST.MF

:: Step 5 – package into final fat jar
cd fatjar
jar cfm ..\%JAR_FILENAME% META-INF\MANIFEST.MF .
cd ..

echo #### Done #### run with: java -jar %JAR_FILENAME%

:: Step 6 – generate a .vbs launcher next to the jar
::   javaw.exe (note the w) is Java's windowless variant — it launches
::   GUI apps with zero console window, exactly like double-clicking a
::   native .exe. The .vbs acts as a zero-install shim that invokes it.
::   --enable-native-access is kept so Argon2's JNA bridge still works.
::   WScript.Shell.Run(..., 0, False) = hidden window, non-blocking.
call :MAKE_LAUNCHER

:: Step 7 – attempt to register .jar → javaw.exe file association
::   Modern JDK (9+) no longer sets this automatically. Without it,
::   double-clicking a .jar does nothing or opens it as a zip.
::   ftype + assoc require elevation; we attempt silently and warn if
::   it fails — the .vbs launcher works regardless so this is optional.
call :FIX_JAR_ASSOC

echo.
echo #### All done ####
echo   Double-click launcher: %~dp0%JAR_FILENAME:.jar=.vbs%
echo   Or run directly:       java -jar %JAR_FILENAME%
goto :eof

:: ====================================================================
:: FUNCTION: MAKE_LAUNCHER
::   Writes a .vbs file alongside the jar that launches it via javaw
::   with no console window. Safe to re-run — overwrites each build.
:: ====================================================================
:MAKE_LAUNCHER
set "VBS_FILE=%JAR_FILENAME:.jar=.vbs%"
:: Write each line of the VBScript using echo + redirect
:: The script resolves its own directory at runtime so it works from
:: any location (Desktop shortcut, mapped drive, etc.)
(
    echo ' JavaPasswordVault — windowless launcher
    echo ' Double-click this file to start the app with no console window.
    echo ' Requires Java (javaw.exe) to be on the system PATH.
    echo Set sh = CreateObject("WScript.Shell"^)
    echo ' Resolve the directory this .vbs lives in
    echo scriptDir = Left(WScript.ScriptFullName, InStrRev(WScript.ScriptFullName, "\")^)
    echo jarPath = scriptDir ^& "%JAR_FILENAME%"
    echo ' Run javaw (no console^) with native access for Argon2 JNA bridge
    echo cmd = "javaw --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -jar """ ^& jarPath ^& """"
    echo ' WindowStyle 0 = hidden, bWaitOnReturn False = non-blocking
    echo sh.Run cmd, 0, False
) > "%VBS_FILE%"
echo [Launcher] Created: %VBS_FILE%
goto :eof

:: ====================================================================
:: FUNCTION: FIX_JAR_ASSOC
::   Registers .jar files to open with javaw.exe system-wide.
::   Requires Administrator elevation — skips gracefully if not elevated.
::   Uses JAVA_HOME if set, otherwise searches PATH for javaw.exe.
:: ====================================================================
:FIX_JAR_ASSOC
:: Locate javaw.exe — prefer JAVA_HOME, fall back to PATH
set "JAVAW_EXE="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW_EXE=%JAVA_HOME%\bin\javaw.exe"
)
if not defined JAVAW_EXE (
    for %%I in (javaw.exe) do set "JAVAW_EXE=%%~$PATH:I"
)
if not defined JAVAW_EXE (
    echo [Assoc] javaw.exe not found — skipping file association.
    goto :eof
)

:: Attempt to write the ftype/assoc entries (silently fails if not elevated)
:: --enable-native-access kept so any .jar using JNA works when double-clicked
ftype jarfile="%JAVAW_EXE%" --enable-native-access=ALL-UNNAMED -jar "%%1" %%* >nul 2>&1
assoc .jar=jarfile >nul 2>&1

:: Check if assoc actually took by reading it back
assoc .jar 2>nul | find "jarfile" >nul 2>&1
if %errorlevel% == 0 (
    echo [Assoc] .jar now opens with: %JAVAW_EXE%
) else (
    echo [Assoc] Could not set file association (not elevated^).
    echo         To fix: right-click this .bat, Run as Administrator, then -j again.
    echo         The .vbs launcher works regardless — no admin needed.
)
goto :eof

:: ====================================================================
:: FUNCTION: TAR_UP
::   On Windows we create a .zip archive instead of a .tgz because
::   PowerShell's Compress-Archive is available natively on Win 10/11.
::   The -d flag also copies the zip to the user's Downloads folder.
::
::   Note: unlike the Linux version, we do NOT rename the directory
::   because robocopy staging avoids the rename side-effect entirely.
::   The .git directory is excluded to keep the archive clean.
:: ====================================================================
:TAR_UP
:: Resolve the parent directory and current folder name
for %%I in ("%cd%") do (
    set "CURRENT_DIR=%%~nxI"
    set "PARENT_DIR=%%~dpI"
)

:: Remove trailing backslash from parent path for clean concatenation
set "PARENT_DIR=%PARENT_DIR:~0,-1%"

set "ZIP_PATH=%PARENT_DIR%\java-password-vault.zip"

:: Remove stale archive if present
if exist "%ZIP_PATH%" del /q "%ZIP_PATH%"

:: Stage into a temp copy that excludes .git (mirrors tar --exclude=.git)
set "STAGE=%TEMP%\java-password-vault-stage"
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%STAGE%\java-password-vault"

:: robocopy: /e = recurse subdirs incl. empty, /xd = exclude .git dir
robocopy "%cd%" "%STAGE%\java-password-vault" /e /xd ".git" >nul

:: Compress the staged copy using PowerShell (no third-party tools needed)
powershell -NoProfile -Command ^
    "Compress-Archive -Path '%STAGE%\java-password-vault' -DestinationPath '%ZIP_PATH%' -Force"

:: Clean up staging directory
rmdir /s /q "%STAGE%"

echo Archive created: %ZIP_PATH%

:: Optionally copy to Downloads (mirrors the -d flag behaviour)
if "%DOWNLOADS%"=="true" (
    copy /y "%ZIP_PATH%" "%USERPROFILE%\Downloads\" >nul
    echo Copied to: %USERPROFILE%\Downloads\java-password-vault.zip
)
goto :eof
