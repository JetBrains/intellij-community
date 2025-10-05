def foo():
    c = bar()
    return c


def bar() -> C:
    class C(object):
        pass

    c = C()
    return c