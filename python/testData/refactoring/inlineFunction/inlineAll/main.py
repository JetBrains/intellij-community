def fo<caret>o(arg):
    local = 1
    if arg:
        another = 2
    else:
        another = 3
    return local


def bar():
    x = 1
    res = foo(x)


def baz():
    y = 2
    res = foo(y)


z = 1
res = foo(z)
