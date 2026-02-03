def f(x):
    """
    :type x: p1.m1.Foo
    """


def test():
    f(<warning descr="Expected type 'Foo', got 'int' instead">10</warning>)
