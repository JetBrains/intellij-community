class C:
    @staticmethod
    def foo():
        C.baz()

    @staticmethod
    def baz():
        print "hello world"