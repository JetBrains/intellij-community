class Callable(object):
    pass


def f(g):
    if callable(g):
        g()
