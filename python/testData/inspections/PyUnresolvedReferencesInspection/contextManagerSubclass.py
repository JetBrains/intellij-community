class C(object):
    def __enter__(self):
        return self


class D(C):
    def foo(self):
        pass


with D() as cm:
    cm.foo()  # pass
