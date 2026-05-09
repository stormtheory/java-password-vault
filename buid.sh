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
  #https://repo1.maven.org/maven2/de/mkammerer/argon2-jvm/
  #https://repo1.maven.org/maven2/de/mkammerer/argon2-jvm-nolibs/
  #https://repo1.maven.org/maven2/net/java/dev/jna/jna/
  #https://www.bouncycastle.org/download/bouncy-castle-java/#latest

#https://github.com/xerial/sqlite-jdbc/releases

ARGON2_LIB='argon2-jvm-2.12.jar'
ARGON2_NOLIB='argon2-jvm-nolibs-2.12.jar'
JNA_LIB='jna-5.18.1.jar'
BOUNCY_HOUSE_LIB='bcprov-jdk18on-1.84.jar'
SQLITE_LIB='sqlite-jdbc-3.53.0.0.jar'

JAR_FILENAME=JavaPasswordVault.jar
DIR_NAME=java-password-vault

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
  -c             Copy the tar to the downloads directory
  -i             Runs the build function
  -b             Runs the build function
  -r             Starts the GUI program
  -j             Create Jar file
  -d             debug

  -a             pass arguments

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

        if [ "$current_dir" == "$DIR_NAME" ];then
        tar --exclude="$DIR_NAME/.git" -czvf ../$DIR_NAME.tgz ../$DIR_NAME
        else
        echo "  Not $current_dir looking for $DIR_NAME"
        mv ../$current_dir ../$DIR_NAME
        tar --exclude="$DIR_NAME/.git" -czvf ../$DIR_NAME.tgz ../$DIR_NAME
        fi

        if [ "$DOWNLOADS" == true ];then
        cp -v ../$DIR_NAME.tgz ~/Downloads
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
    cd fatjar && jar xf ../lib/$SQLITE_LIB && jar xf ../lib/$ARGON2_LIB && jar xf ../lib/$ARGON2_NOLIB && jar xf ../lib/$JNA_LIB && jar xf ../lib/$BOUNCY_HOUSE_LIB && cd ..

    # ===== Strip signature files — required for signed JARs like Bouncy Castle =====
    rm -f fatjar/META-INF/*.SF
    rm -f fatjar/META-INF/*.RSA
    rm -f fatjar/META-INF/*.DSA

    # ===== Write manifest =====
    mkdir -p fatjar/META-INF
    printf 'Manifest-Version: 1.0\nMain-Class: GUI\n\n' > fatjar/META-INF/MANIFEST.MF

    # ===== Package =====
    cd fatjar && jar cfm ../$JAR_FILENAME META-INF/MANIFEST.MF . && cd ..
    echo "#### Done #### run with: java -jar $JAR_FILENAME"
}

BUILD() {
        rm -f ./bin/*
        echo "javac -cp \".:lib/$SQLITE_LIB:lib/$ARGON2_LIB:lib/$ARGON2_NOLIB:lib/$BOUNCY_HOUSE_LIB:lib/$JNA_LIB:bin\" -d bin *.java"
        
        if [ "$DEBUG" != true ];then
          javac -cp ".:lib/$SQLITE_LIB:lib/$ARGON2_LIB:lib/$ARGON2_NOLIB:lib/$BOUNCY_HOUSE_LIB:lib/$JNA_LIB:bin" -d bin *.java
        else
          javac -Xlint:deprecation -cp ".:lib/$SQLITE_LIB:lib/$ARGON2_LIB:lib/$ARGON2_NOLIB:lib/$BOUNCY_HOUSE_LIB:lib/$JNA_LIB:bin" -d bin *.java
        
        fi
}

RUN(){
      echo "java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp \".:lib/$SQLITE_LIB:lib/$ARGON2_LIB:lib/$ARGON2_NOLIB:lib/$BOUNCY_HOUSE_LIB:lib/$JNA_LIB:bin\" GUI $ARGUMENTS"
      java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp ".:lib/$SQLITE_LIB:lib/$ARGON2_LIB:lib/$ARGON2_NOLIB:lib/$BOUNCY_HOUSE_LIB:lib/$JNA_LIB:bin" GUI $ARGUMENTS
}

DEBUG=false
HELP=true
ARGUMENTS=
# 🔍 Parse options
while getopts ":a:ijdcbrh" opt; do
  case ${opt} in
    a)
        ARGUMENTS=$OPTARG
        ;;
    c)
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
    d)  DEBUG=true
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
