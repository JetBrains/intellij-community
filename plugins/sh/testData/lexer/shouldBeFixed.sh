let "varone = 1" "vartwo = ++varone"; echo $varone, $vartwo
let "varone = 1" "vartwo = varone++"; echo $varone, $vartwo
$(((count != 1)) && echo)