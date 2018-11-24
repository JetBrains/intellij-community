def f(x, bar, **kwargs):
    print(foo, kwargs)


f(42, bar=None, extra1=1, extra2=2)