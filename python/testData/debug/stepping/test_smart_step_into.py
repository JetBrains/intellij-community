def foo():
    return bar() + baz() + bar() + barbaz(10) + barbaz(bar()) + add(bar(), baz())


def bar():
    x = 42
    return x


def baz():
    y = bar() + bar()
    return y


def barbaz(i):
    return i


def add(x, y):
    return x + y


if __name__ == '__main__':
    foo()
    foo()
