class C:
    @classmethod
    def baz(cls):
        print('foo', cls)

    @classmethod
    def foo(cls):
        cls.baz()
