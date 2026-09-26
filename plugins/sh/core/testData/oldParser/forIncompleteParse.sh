#error markers must be present, but the incomplete if should be parsed without remaining elements
for i in 1 2 3 4 5
do
   echo "Welcome $i times"
done
for f in a; do; done
if [ "foo" = "foo" ]; then
    echo 1
fi;