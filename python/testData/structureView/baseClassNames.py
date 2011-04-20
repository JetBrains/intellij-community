class B1:
    def f(self, x):
        return x + self.y

class B2(object):
    @staticmethod
    def g(x):
        return x

class C(B1, B2):
    pass

class D1(C):
    pass

class D2(C):
    pass
