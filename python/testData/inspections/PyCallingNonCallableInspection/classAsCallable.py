# PY-4061
def test_class_as_callable():
    class C1(object):
        def __init__(self):
            pass
        def f(self):
            pass
    class C2(object):
        def g(self):
            pass
    class C3:
        pass
    c1 = C1() #pass
    c2 = C2() #pass
    c3 = C3() #pass
    c1.__class__().f() #pass
    c2.__class__().g() #pass
    c3.__class__() #pass
