class C:
    @classmethod
    def baz(cls):
        print "hello world"

    @classmethod
    def foo(cls):
        cls.baz()