class C:
    @staticmethod
    def baz():
        print "hello world"

    @staticmethod
    def foo():
        C.baz()