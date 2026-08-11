class Foo:
    @property
    def bar(self):
        import warnings
        warnings.warn("this is deprecated", DeprecationWarning, 2)

    @property
    def shape(self):
        ...

foo = Foo()
foo.<warning descr="this is deprecated">bar</warning>
foo.shape
