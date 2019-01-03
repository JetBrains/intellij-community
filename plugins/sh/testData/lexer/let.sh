let "myvar = 16 << 2"; echo $myvar
let "varone = 1" "vartwo = ++varone"; echo $varone, $vartwo
let "varone = 1" "vartwo = varone++"; echo $varone, $vartwo
let "myvar = 5"; echo $myvar

let o=1+1
let a=1 b=2
echo $a, $b, $o