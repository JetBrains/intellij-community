class MyMeta(type):
    def __getitem__(self, item):
        return 0

    def foo(cls):
        pass


class C(str, metaclass=MyMeta):
    pass


print(C['foo'])
print(C.foo())