def f(x, foo, y=None, **kwargs):
    print(foo, kwargs)


f(42, foo=None, extra1=1, extra2=2)