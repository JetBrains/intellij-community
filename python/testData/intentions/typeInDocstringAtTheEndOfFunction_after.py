def func2(x):
    """

    :rtype: object
    """
    y = func1(x.keys())
    return y.startswith('foo')