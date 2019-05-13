def f(foo, **kwargs):
    print(foo, kwargs)


f(foo=None, extra1=1, extra2=2)