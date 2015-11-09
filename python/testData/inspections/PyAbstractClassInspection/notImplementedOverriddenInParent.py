class A:
    def foo(self):
        raise NotImplementedError


class B(A):
    def foo(self):
        print('foo')


class C(B):
    pass
