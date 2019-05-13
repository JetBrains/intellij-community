class B:
    def foo(self, arg1, arg2=None, arg3=None, arg4=None):
        pass


class C(B):
    def foo(self, arg1, arg2=None, arg3=None, **kwargs): #pass
        pass
