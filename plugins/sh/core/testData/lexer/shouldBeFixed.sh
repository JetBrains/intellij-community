$(((count != 1)) && echo) #issue199



 \


for case in a; do
echo
done;

${a${b}}                  #paramExpansionNested

`cat <<EOF                #issue473
X
EOF`
$(cat <<EOF
X
EOF
)

$(${)                     #issue398

[[ (a =~ "b") ]]          #issue412