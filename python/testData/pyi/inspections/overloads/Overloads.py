from m1 import f, C


print(f(10))
print(f('foo'))
print(f(<warning descr="Expected type 'int', got 'dict[int, int]' instead">{1: 2}</warning>))

c = C()
print(c[10])
print(c['foo'])
print(c[<warning descr="Expected type 'int', got 'dict[int, int]' instead">{1: 2}</warning>])
