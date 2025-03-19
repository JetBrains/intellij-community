class MyMeta(type):
    def __getattribute__(self, item):
        raise RuntimeError


class MyClass(metaclass=MyMeta):
    attr = 42
