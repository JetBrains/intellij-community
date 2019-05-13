def foo():
    c = bar()
    return c


def bar():
    class C(object):
        pass

    c = C()
    return c