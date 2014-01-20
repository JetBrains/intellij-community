from m1 import C

c = C()
print(C.foo(), C.<warning descr="Unresolved attribute reference 'bar' for class 'C'">bar</warning>())
print(c.<warning descr="Unresolved attribute reference 'foo' for class 'C'">foo</warning>())