from m1 import m1m1
print(m1m1)

from m1 import <weak_warning descr="'m1m2' is not declared in __all__">m1m2</weak_warning>
print(m1m2)

from m1 import m1m1, <weak_warning descr="'m1m2' is not declared in __all__">m1m2</weak_warning>
print(m1m1)


from pkg.A import B
print(B)


from pkg.A import C
print(C)