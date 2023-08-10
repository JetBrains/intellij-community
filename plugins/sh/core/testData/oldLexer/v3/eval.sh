eval [ "a" ]
eval "echo $"
for f in a; do eval [ "a" ]; done
case a in a) echo [ "a" ];; esac
y=`eval ls -l`