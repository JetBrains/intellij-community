class Foo:
    @property
    def bar(self):
        import warnings
        warnings.warn("this is deprecated", DeprecationWarning, 2)

foo = Foo()
foo.<warning descr="this is deprecated">bar</warning>
