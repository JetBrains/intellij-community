"$(||)"
[ a  ]
[ a | b ]
echo $((1+(++1)))

echo [ foo[1] ]
echo [foo[1]]

[[ ((pwd)) ]]
[[ ((1+1)) ]]

values[indx]=1

echo [ "foo" = "foo" ]

# fixme, this is not an arithmetic expansion, but two nested parenthesis expressions
#[[ ((a)) ]]

#========== FROM OLD PARSER ===========
`cat <<EOF
 X
EOF`
X

((a ? b ? c :d : c))

for f in 1; do {
echo 1
}    done


function a
for f in 1; do echo "k"; done;

if read pid > c; then
      a
fi

if echo "sdfsd"; then
   echo "sdfsdf"
else
   echo "sdfsdf";
fi