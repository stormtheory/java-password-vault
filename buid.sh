#!/usr/bin/bash
cd "$(dirname "$0")"

####################################################################
##
##                   LINUX ONLY
##        Script is to make building, launching, 
## and running easier with command line (CLI) arguments 
##
##               With Love, Stormtheory
##
####################################################################

#https://central.sonatype.com/artifact/de.mkammerer/argon2-jvm/versions
# argon2-jvm:jar:2.12
#https://github.com/xerial/sqlite-jdbc/releases

ARGON2_LIB='argon2-jvm-2.12.jar'
SQLITE_LIB='sqlite-jdbc-3.53.0.0.jar'

JAR_FILENAME=JavaPasswordVault.jar

# No running as root!
ID=$(id -u)
if [ "$ID" == '0'  ];then
        echo "Not safe to run as root... exiting..."
        exit
fi



# 🧾 Help text
show_help() {
  cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  -d             Copy the tar to the downloads directory
  -i             Runs the build function
  -b             Runs the build function
  -r             Starts the GUI program
  -j             Create Jar file

  -h             Show this help message

Example:
  $0 -br
EOF
}

# 🔧 Default values
DOWNLOADS=false


TAR_UP() {
        pwd_current=$(pwd)
        current_dir_path=$(echo "${pwd_current%/*}")
        current_dir=$(echo "${pwd_current##*/}")

        DIR_NAME=java-password-vault

        if [ "$current_dir" == "$DIR_NAME" ];then
        tar --exclude="$DIR_NAME/.git" -czvf ../java-password-vault.tgz ../$DIR_NAME
        else
        echo "  Not $current_dir looking for $DIR_NAME"
        mv ../$current_dir ../$DIR_NAME
        tar --exclude="$DIR_NAME/.git" -czvf ../java-password-vault.tgz ../$DIR_NAME
        fi

        if [ "$DOWNLOADS" == true ];then
        cp -v ../java-password-vault.tgz ~/Downloads
        fi
}

JAR(){
    # ===== Clean old build =====
    rm -f bin/* $JAR_FILENAME
    rm -rf fatjar 

    # ===== Compile =====
    BUILD

    # ===== Build fat jar =====
    mkdir -p fatjar
    cp -r bin/* fatjar/
    cd fatjar && jar xf ../lib/$SQLITE_LIB && jar xf ../lib/$ARGON2_LIB && cd ..

    # ===== Write manifest =====
    mkdir -p fatjar/META-INF
    printf 'Manifest-Version: 1.0\nMain-Class: GUI\n\n' > fatjar/META-INF/MANIFEST.MF

    # ===== Package =====
    cd fatjar && jar cfm ../$JAR_FILENAME META-INF/MANIFEST.MF . && cd ..

    echo "#### Done #### run with: java -jar $JAR_FILENAME"
}

BUILD() {
        rm -f ./bin/*
        echo "javac -cp \".:lib/$SQLITE_LIB\" -cp \".:lib/$ARGON2_LIB\" -d bin *.java"
        javac -cp ".:lib/$SQLITE_LIB" -cp ".:lib/$ARGON2_LIB" -d bin *.java
}

RUN(){
        echo "java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp \"bin:$SQLITE_LIB\" -cp \"bin:$ARGON2_LIB\" GUI"
        java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp "bin:$SQLITE_LIB" -cp "bin:$ARGON2_LIB" GUI
}

HELP=true
# 🔍 Parse options
while getopts ":ijdbrh" opt; do
  case ${opt} in
    d)
        TAR_UP=true
        DOWNLOADS=true
        HELP=false
        ;;
    j)  
        JAR
        exit
        ;;
    i)
        BUILD=true
        HELP=false
        ;;
    b)
        BUILD=true
        HELP=false
        ;;
    r)  RUN=true
        HELP=false
        ;;
    h)
      show_help
      exit 0
      ;;
    \?)
      echo "❌ Invalid option: -$OPTARG" >&2
      show_help
      exit 1
      ;;
    :)
      echo "❌ Option -$OPTARG requires an argument." >&2
      show_help
      exit 1
      ;;
  esac
done


if [ "$BUILD" == true ];then
    BUILD
fi

if [ "$TAR_UP" == true ];then
    TAR_UP
fi

if [ "$RUN" == true ];then
    RUN
fi


if [ "$HELP" == true ];then
        show_help
        exit 1
fi
