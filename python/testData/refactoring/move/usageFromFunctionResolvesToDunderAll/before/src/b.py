__all__ = ['foo']


class C(object):
    def foo(self):
        pass


_c = C()


def _init(g):
    for name in dir(_c):
        if not name.startswith('_'):
            g[name] = getattr(_c, name)


_init(globals())