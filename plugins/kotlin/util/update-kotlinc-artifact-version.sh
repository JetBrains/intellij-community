#!/usr/bin/env bash
set -e # Exit if one of commands exit with non-zero exit code
set -u # Treat unset variables and parameters other than the special parameters ‘@’ or ‘*’ as an error

# FYI: This script is referenced in TeamCity configs

if [ $# -ne 1 ]; then
    echo "Usage: update-kotlinc-artifact-version.sh VERSION_TO_UPDATE_TO"
    exit 1
fi

root="$(git rev-parse --show-toplevel)"

extract_version_using_regex() {
    cat "${root}/.idea/libraries/kotlinc_kotlin_compiler.xml" | grep -oP -- "$1" | head -1
}

current_version=""
current_version="${current_version:-$(extract_version_using_regex "\d*.\d*.\d*-SNAPSHOT")}"
current_version="${current_version:-$(extract_version_using_regex "\d*.\d*.\d*-M1-\d*")}"
current_version="${current_version:-$(extract_version_using_regex "\d*.\d*.\d*-M2-\d*")}"
current_version="${current_version:-$(extract_version_using_regex "\d*.\d*.\d*-RC-\d*")}"
current_version="${current_version:-$(extract_version_using_regex "\d*.\d*.\d*-release-\d*")}"

if [ -z "$current_version" ]; then
    echo "Cannot determine current kotlinc version"
    exit 1
fi

find "${root}/.idea" -type f -exec sed -i -e "s/$current_version/$1/g" {} \;

if [ -d "${root}/community/.idea" ]; then
    find "${root}/community/.idea" -type f -exec sed -i "s/$current_version/$1/g" {} \;
fi
