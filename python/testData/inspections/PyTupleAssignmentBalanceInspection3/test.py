a, b, c = <warning descr="Too many values to unpack">(1, 2, 3, 4)</warning>

#PY-4357
c = 1, 2, 3
a, b = <warning descr="Too many values to unpack">c</warning>