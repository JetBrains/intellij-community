class Foo:
    def foo(self):
        pass


class Baz:
    @staticmethod
    def baz():
        foo = Foo()
        foo.foo()
