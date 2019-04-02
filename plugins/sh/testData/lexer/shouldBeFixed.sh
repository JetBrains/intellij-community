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


\
b                        #lineContinuation


for D1 in "$COMPILE_ONLY_RESOURCES_DIR"; do

                echo 1

            done


case $x in


pattern)
            for D1 in "$COMPILE_ONLY_RESOURCES_DIR"; do

                echo 1

            done

  ;;
esac




