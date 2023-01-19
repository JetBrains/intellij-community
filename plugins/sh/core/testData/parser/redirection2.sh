cat file1.txt >/dev/null file2.txt
cat - <<<stdin file.txt
cat - <<< stdin file.txt
# equivalent redirects
>& x echo out
>&x echo out
&>x echo out
&> x echo out
# at eof
echo >out