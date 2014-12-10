def f(x):
    return x.foo + x.bar()


def g(x):
    return x.lower()


def test(x):
    x.foo
    f(x)
    g(<warning descr="Expected type '{lower}', got '{foo}' instead">x</warning>)

    z = 'foo'
    f(<warning descr="Expected type '{foo, bar}, got 'str' instead">x</warning>)
    g(x)
