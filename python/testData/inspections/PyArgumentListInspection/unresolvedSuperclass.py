class Foo(NoSuchClassAnywhere):
    pass


class X(Foo):
    def __init__(self):
        Foo.__init__(self, 'a')
