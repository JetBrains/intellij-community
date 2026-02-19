def func2(x):
    """

    :rtype: Any
    """
    y = func1(x.keys())
    return y.startswith('foo')