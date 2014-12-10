def f(x):
    """
    :type x: str
    """
    pass


def g(x):
    return x.lower()


def test(x, y):
    x.upper()
    f(x)
    g(x)

    y.foo()
    f(<warning descr="Expected type 'str', got '{foo}' instead">y</warning>)
    g(<warning descr="Expected type '{lower}', got '{foo}' instead">y</warning>)
