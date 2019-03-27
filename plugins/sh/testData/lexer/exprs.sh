*
-
+
/

$(( 1 * 1))
$(( 1 - 1))
$(( 1 + 1))
$(( 1 / 1))


$(( 1--))
$(( 1++))
$(( +1++))
$(( -1--))

for (( c=1; c<=5; c++ ))
do
   echo "Welcome $c times"
done


git clone --mirror hg::$(pwd)/hg/$repo git/$repo