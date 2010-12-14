<warning descr="Assignment can be replaced with augmented assignment">var = var * 2</warning>
<warning descr="Assignment can be replaced with augmented assignment">var_1 = var_1 + 1</warning>

str = "string"

var_2 = var_2 + str

var_3 = var_3
var_4 = 1
var_5 = var_5 + "string"
var_6 = var_6 + var_4

var_7 += 2

#PY-2482
<warning descr="Assignment can be replaced with augmented assignment">var = 2 + var</warning>

# PY-2483
<warning descr="Assignment can be replaced with augmented assignment">list[0] = list[0] + 1</warning>