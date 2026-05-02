#!/usr/bin/bash
cd "$(dirname "$0")"

####################################################################
##
##                   LINUX ONLY
##        Script is to make building, launching, 
## and running easier with command line (CLI) arguments 
##
##               With Love, Stormtheory
####################################################################



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

  -h             Show this help message

Example:
  ./$0 -br
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

BUILD() {
        echo 'javac -cp ".:sqlite-jdbc-3.53.0.0.jar" -d go *.java'
        javac -cp ".:sqlite-jdbc-3.53.0.0.jar" -d go *.java
}

RUN(){
        echo 'java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp \"go:sqlite-jdbc-3.53.0.0.jar\" GUI'
        java --enable-native-access=ALL-UNNAMED -Dorg.sqlite.tmpdir=. -cp "go:sqlite-jdbc-3.53.0.0.jar" GUI
}

HELP=true
# 🔍 Parse options
while getopts ":idrbh" opt; do
  case ${opt} in
    d)
        DOWNLOADS=true
        HELP=false
        ;;
    i)
        BUILD
        HELP=false
        ;;
    b)
        BUILD
        HELP=false
        ;;
    r)  RUN
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
if [ "$HELP" == true ];then
        show_help
        exit 1
fi