def f(foo, x, **kwargs):
    print(foo, kwargs)


f(42, x=None, extra1=1, extra2=2)