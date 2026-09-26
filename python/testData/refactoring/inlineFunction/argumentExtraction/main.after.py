def foo(x, y, z):
    print(x)
    print(y)
    print(z)
    return x


def id(x):
    return x


def bar():
    x = id(1)
    y = id(2)
    z = id(3)
    print(x)
    print(y)
    print(z)
    res = x
