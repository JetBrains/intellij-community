def func(foo, bar):
    pass


def usage(**kwargs):
    func(1, **kwargs)
    func(1, **kwargs, **kwargs)
    func(1, **kwargs)
    func(1, **kwargs, **kwargs)
    func(1, 2)
    func(1, 2)
    func(1, 2)
    func(1, 2)
