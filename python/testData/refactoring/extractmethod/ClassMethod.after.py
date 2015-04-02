class C:
    @classmethod
    def foo(cls):
        cls.baz()

    @classmethod
    def baz(cls):
        print('foo', cls)
