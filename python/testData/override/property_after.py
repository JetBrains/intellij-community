class C(object):
    @property
    def foo(self):
        return None


class D(C):
    @property
    def foo(self):
        return super(D, self).foo

