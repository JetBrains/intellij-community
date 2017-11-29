def f(x, foo, y, **kwargs):
    print(foo, kwargs)


f(42, foo=None, y=None, extra1=1, extra2=2)