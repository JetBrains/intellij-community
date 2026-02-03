def func(foo, bar, baz=42, **kwargs):
    pass


def wrapper(**kwargs):
    func(42, **kwargs)
    func(42, **kwargs, **kwargs)
    func(42, bar=42, **kwargs)
    func(42, bar=42, **kwargs, **kwargs)
    func(42, extra=42, **kwargs)
    func(42, extra=42, **kwargs, **kwargs)
    func(42, bar=42, extra=42, **kwargs)
    func(42, bar=42, extra=42, **kwargs, **kwargs)
