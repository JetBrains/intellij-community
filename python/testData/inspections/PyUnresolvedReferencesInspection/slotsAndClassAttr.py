class Singleton(object):
    __slots__ = ()
    data = {}

    def foo(self):
        self.<warning descr="'Singleton' object has no attribute 'data'">data</warning> = {'a': 1}

Singleton.data = {'a': 1}
Singleton().__class__.data = {'a': 1}