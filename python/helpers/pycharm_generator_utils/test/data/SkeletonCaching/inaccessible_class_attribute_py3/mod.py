class MyMeta(type):
    def __getattribute__(self, item):
        raise RuntimeError


class MyClass(meta=MyMeta):
    attr = 42
