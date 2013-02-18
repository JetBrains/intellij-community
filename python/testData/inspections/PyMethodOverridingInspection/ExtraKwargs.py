class B:
    def foo(self, x=1):
        pass


class C(B):
    def foo(self, **kwargs):
        pass
