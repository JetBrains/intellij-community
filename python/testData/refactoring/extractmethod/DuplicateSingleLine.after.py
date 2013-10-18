def foo():
    a = 1
    return a


def bar():
    a = foo()
    print a
    a = foo()
    print a
