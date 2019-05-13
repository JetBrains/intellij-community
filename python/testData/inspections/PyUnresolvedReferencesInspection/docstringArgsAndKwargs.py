def f1(foo, bar, *args):
    """
    :param foo: resolves to foo
    :param baz: resolves to *args
    """
    pass

def f2(foo, bar, **kwargs):
    """
    :param foo: resolves to foo
    :param baz: resolves to **kwargs
    """
    pass
