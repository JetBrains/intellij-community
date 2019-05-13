def f(x, foo, **kwargs):
    print(foo, kwargs)


f(42, extra1=1, foo=None, extra2=2)