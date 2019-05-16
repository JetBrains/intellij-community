#!/bin/bash

echo "This script can make any of the files in this directory private."
echo "Enter the number of the file you want to protect:"

PS3="Your choice: "
QUIT="QUIT THIS PROGRAM - I feel safe now."
touch "$QUIT"

chmod go-rwx "$FILENAME"

select FILENAME in *;
do
  case $FILENAME in
        "$QUIT")
          echo "Exiting."
          break
          ;;
        *)
          echo "You picked $FILENAME ($REPLY)"
          ;;
  esac
done
rm "$QUIT"