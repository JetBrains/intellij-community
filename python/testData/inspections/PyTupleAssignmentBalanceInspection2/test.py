a, b, c = <warning descr="Too many values to unpack">(1, 2, 3, 4)</warning>

#PY-4357
c = 1, 2, 3
a, b = <warning descr="Too many values to unpack">c</warning>

#PY-4360
(a, b) = <warning descr="Too many values to unpack">1, 2, 3</warning>
(a, b) = <warning descr="Too many values to unpack">(1, 2, 3)</warning>

#PY-4358
a, b = <warning descr="Too many values to unpack">[1, 2, 3]</warning>
a, b = <warning descr="Too many values to unpack">'str'</warning>
a, b = <warning descr="Too many values to unpack">{1, 2, 3}</warning>
a, b = <warning descr="Too many values to unpack">{1:2, 2: 3, 3:4}</warning>

# PY-6315
def test_tuple_slice():
    def f():
        return 1, 2, 3
    x, y = f()[:2]
