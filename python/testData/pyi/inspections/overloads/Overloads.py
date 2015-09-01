from m1 import f, g, C


print(f(10))
print(f('foo'))
print(f(<warning descr="Expected type 'int', got 'dict[int, int]' instead">{1: 2}</warning>))


g(<warning descr="Expected type 'dict', got 'int' instead">f(10)</warning>)
g(<warning descr="Expected type 'dict', got 'str' instead">f('foo')</warning>)


def unknown_arg_type(x):
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">f(x)</warning>)


c = C()
print(c[10])
print(c['foo'])
print(c[<warning descr="Expected type 'int', got 'dict[int, int]' instead">{1: 2}</warning>])


# TODO: Tests for binary operators


# TODO: Tests for stub-only functions


# TODO: Tests for generics unification
