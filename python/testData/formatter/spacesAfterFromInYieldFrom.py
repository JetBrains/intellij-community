def g():
    yield 42


def f():
    yield from   g()