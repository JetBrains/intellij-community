class MyMeta(type):
    def __getattribute__(self, item):
        raise RuntimeError

    def __getattr__(self, item):
        raise RuntimeError


class MyClass(object):
    __metaclass__ = MyMeta
    attr = 42
