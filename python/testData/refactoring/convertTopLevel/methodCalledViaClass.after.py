class C():
    pass


def method(foo, bar, x):
    print(foo, bar, x)


foo = C()
method(foo.foo, foo.bar, 42)