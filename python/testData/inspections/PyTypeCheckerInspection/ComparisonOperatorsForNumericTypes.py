def f(x):
    print(x < 0, x <= 0, x > 0, x >= 0, x != 0)
    print(x.foo)


print(f(<warning descr="Type 'bool' doesn't have expected attribute 'foo'">True</warning>))
print(f(<warning descr="Type 'int' doesn't have expected attribute 'foo'">0</warning>))
print(f(<warning descr="Type 'float' doesn't have expected attribute 'foo'">3.14</warning>))
