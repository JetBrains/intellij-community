class A(object):
    def f(self, x):
        return self


a = A()
a.f(1).f(2).f(3)
