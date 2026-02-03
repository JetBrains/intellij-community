def foo(x, y, z):
    print(x)
    print(y)
    print(z)
    return x


def id(x):
    return x


def bar():
    res = fo<caret>o(id(1), id(2), z = id(3))
