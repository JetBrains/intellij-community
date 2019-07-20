def foo(x, y, z):
    if x:
        return x + 2
    elif y:
        return y + 2
    else:
        if z:
            return z + 2
        else:
            return 2


def bar():
    a = 1
    b = 2
    c = 3
    res = fo<caret>o(a, b, c)
