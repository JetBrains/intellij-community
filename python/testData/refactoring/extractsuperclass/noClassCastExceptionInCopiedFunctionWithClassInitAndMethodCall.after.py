class Foo:
    def foo(self):
        pass


class Bar:
    @staticmethod
    def baz():
        foo = Foo()
        foo.foo()


class Baz(Bar):
    pass
