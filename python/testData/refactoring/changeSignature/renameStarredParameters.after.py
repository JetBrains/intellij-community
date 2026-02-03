def func(arg1, *foo, **bar):
    foo = list(foo) + [arg1]
    bar = dict(bar)