class Test(object):
    def __new__(cls, foo):
        x = super(Test, cls)
        return x.__new__(cls)
