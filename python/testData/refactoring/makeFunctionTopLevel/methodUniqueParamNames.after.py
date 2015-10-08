class C:
    pass


def method(foo2, bar1, foo, foo1, bar):
    print(foo2, bar1)


c = C()
method(c.foo, c.bar, 1, 2, bar=3)