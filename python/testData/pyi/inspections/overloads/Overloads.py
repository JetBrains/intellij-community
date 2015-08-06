from m1 import C

c = C()
print(c[10])
print(c['foo'])
print(c[<warning descr="Expected type 'int', got 'dict[int, int]' instead">{1: 2}</warning>])
