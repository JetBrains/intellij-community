echo $[ 1 < 1 ]
echo $[ 1 > 1]
echo $[ 1 >= 1]
echo $[ 1 <= 1]
echo $[ 1 == 1]
echo $[ 1 != 1]
echo $[ ++1 ]
echo $[ --1 ]
echo $[ 1 * 1]
echo $(( 1 < 1 ))
echo $[ 1 < 1]

echo for $[ 0x888 + 007 ]



*
-
+
/

$(( 1 * 1))
$(( 1 - 1))
$(( 1 + 1))
$(( 1 / 1))

$((Errors<125?Errors:125))

$(( 1--))
$(( 1++))
$(( +1++))
$(( -1--))

for (( c=1; c<=5; c++ ))
do
   echo "Welcome $c times"
done


git clone --mirror hg::$(pwd)/hg/$repo git/$repo