class Singleton(object):
    __slots__ = ()
    data = {}

    def foo(self):
        self.data = {'a': 1}

Singleton.data = {'a': 1}
Singleton().__class__.data = {'a': 1}