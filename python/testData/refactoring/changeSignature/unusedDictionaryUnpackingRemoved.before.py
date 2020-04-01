def func(foo, bar, **kwargs):
    pass


def usage(**kwargs):
    func(1, **kwargs)
    func(1, **kwargs, **kwargs)
    func(1, **kwargs, extra=42)
    func(1, **kwargs, extra=42, **kwargs)
    func(1, 2, **kwargs)
    func(1, 2, **kwargs, **kwargs)
    func(1, 2, **kwargs, extra=42)
    func(1, 2, **kwargs, extra=42, **kwargs)
