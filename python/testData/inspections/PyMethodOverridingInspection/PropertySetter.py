class B(object):
    @property
    def foo(self):
        return 'foo'

    @foo.setter
    def foo(self, value):
        pass


class C(B):
    @property
    def foo(self):
        return 'bar'

    @foo.setter
    def foo(self, value):  # pass
        pass
