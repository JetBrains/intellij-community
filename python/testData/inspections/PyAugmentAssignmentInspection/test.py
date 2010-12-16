<warning descr="Assignment can be replaced with augmented assignment">var = var * 2</warning>
<warning descr="Assignment can be replaced with augmented assignment">var_1 = var_1 + 1</warning>

str = "string"

<warning descr="Assignment can be replaced with augmented assignment">var_2 = var_2 + str</warning>

var_3 = var_3
var_4 = 1
<warning descr="Assignment can be replaced with augmented assignment">var_5 = var_5 + "string"</warning>
<warning descr="Assignment can be replaced with augmented assignment">var_6 = var_6 + var_4</warning>

var_7 += 2

#PY-2482
<warning descr="Assignment can be replaced with augmented assignment">var = 2 + var</warning>

# PY-2483
<warning descr="Assignment can be replaced with augmented assignment">list[0] = list[0] + 1</warning>

# PY-2488
<warning descr="Assignment can be replaced with augmented assignment">a = a ** 1</warning>

<warning descr="Assignment can be replaced with augmented assignment">x = x % 3</warning>
<warning descr="Assignment can be replaced with augmented assignment">x = x | 3</warning>
<warning descr="Assignment can be replaced with augmented assignment">x = x & 3</warning>
<warning descr="Assignment can be replaced with augmented assignment">x = x ^ 3</warning>
