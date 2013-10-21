class B:
    def foo(self, **kwargs):
        pass


class C(B):
    def foo(self, arg1=None, **kwargs): # pass
        pass
