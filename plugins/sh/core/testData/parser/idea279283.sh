#!/usr/bin/zsh

echo "This echo is highlighted"
file_path="/path/to/file/sql_dump.sql.bz2"
re_file_ext='.*\.\(bz2\|gz\|zip\|xz\|sql\)$'
file_ext=$(expr "$file_path" : "$re_file_ext")
bootstrapver=$(curl https://go.dev/VERSION?m=text)
echo "$file_ext"
case "$file_ext" in
  'gz'  ) deflatecmd='gunzip --stdout' ;;
  'bz2' ) deflatecmd='bzcat' ;;
  'xz'  ) deflatecmd='xzcat' ;;
  'zip' ) deflatecmd='unzip -c' ;;
esac

test_function() {
  echo 'Highlighting has stopped (in part)'
}