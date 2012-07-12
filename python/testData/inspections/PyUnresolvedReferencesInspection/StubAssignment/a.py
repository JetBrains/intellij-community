import m1

x = 1 * m1.C()
print(x.foo)
print(x.<warning descr="Unresolved attribute reference 'bar' for class 'C'">bar</warning>)
