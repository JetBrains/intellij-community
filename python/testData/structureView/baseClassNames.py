import lib1

class B1:
    def f(self, x):
        return x

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

# PY-3714
class D3(lib1.C):
    pass

# PY-3731
class D4(foo.bar.C):
    pass