class A(object):
    def foo(self):
        pass


C = A


class B(C):
    pass


b = B()
b.foo() #pass
