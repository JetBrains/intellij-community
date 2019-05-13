def func(arg1, *args, **kwargs):
    args = list(args) + [arg1]
    kwargs = dict(kwargs)