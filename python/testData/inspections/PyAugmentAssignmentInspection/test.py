<weak_warning descr="Assignment can be replaced with augmented assignment">var = var * 2</weak_warning>
<weak_warning descr="Assignment can be replaced with augmented assignment">var_1 = var_1 + 1</weak_warning>

str = "string"

<weak_warning descr="Assignment can be replaced with augmented assignment">var_2 = var_2 + str</weak_warning>

var_3 = var_3
var_4 = 1
<weak_warning descr="Assignment can be replaced with augmented assignment">var_5 = var_5 + "string"</weak_warning>
<weak_warning descr="Assignment can be replaced with augmented assignment">var_6 = var_6 + var_4</weak_warning>

var_7 += 2

#PY-2482
<weak_warning descr="Assignment can be replaced with augmented assignment">var = 2 + var</weak_warning>

# PY-2483
<weak_warning descr="Assignment can be replaced with augmented assignment">list[0] = list[0] + 1</weak_warning>

# PY-2488
<weak_warning descr="Assignment can be replaced with augmented assignment">a = a ** 1</weak_warning>

<weak_warning descr="Assignment can be replaced with augmented assignment">x = x % 3</weak_warning>
<weak_warning descr="Assignment can be replaced with augmented assignment">x = x | 3</weak_warning>
<weak_warning descr="Assignment can be replaced with augmented assignment">x = x & 3</weak_warning>
<weak_warning descr="Assignment can be replaced with augmented assignment">x = x ^ 3</weak_warning>

#PY-2514
dy = 1 - dy

#PY-6331
var = "string"
var = "some " + var

#PY-6490
foo = "a"
bar = "b"
foo = bar + foo