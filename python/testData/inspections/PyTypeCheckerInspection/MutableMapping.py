def foo(x):
    """
    :type x: collections.MutableMapping
    :rtype: dict
    """
    return {v: k for k, v in x.iteritems()}

d = dict(a=1, b=2)
foo(d)

l = [i for i in range(10)]
foo(<warning descr="Expected type 'MutableMapping', got 'List[int]' instead">l</warning>)