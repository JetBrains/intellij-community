class Test(object):
    def __new__(cls, foo):
        x = super(Test, cls).__new__(cls)
        x.foo = foo
        return x
