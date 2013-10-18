class C(object):
    def __new__(cls):
        return D()


class D(object):
    def foo(self):
        pass
