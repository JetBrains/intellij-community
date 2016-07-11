def foo(x):
    """
    :type x: collections.MutableMapping
    :rtype: int
    """
    return {v: k for k, v in x.iteritems()}

d = dict(a=1, b=2)
foo(d)