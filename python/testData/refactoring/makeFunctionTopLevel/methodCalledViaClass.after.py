class C():
    pass


def method(foo, bar, x):
    print(foo, bar, x)


c = C()
method(c.foo, c.bar, 42)
method()