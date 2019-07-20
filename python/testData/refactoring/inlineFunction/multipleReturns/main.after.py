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
    if a:
        result = a + 2
    elif b:
        result = b + 2
    else:
        if c:
            result = c + 2
        else:
            result = 2
    res = result
