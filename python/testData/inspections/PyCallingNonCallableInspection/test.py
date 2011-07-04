<warning descr="'tuple' object is not callable">(1,2)()</warning>

class SM(object):
    def my_method(): pass
    my_method = staticmethod(my_method)

    def q(self):
        self.my_method()

def concealer():
    class prog(object):
        def __call__(self): pass

    pr = prog()
    pr()

def test_module():
    import sys
    <warning descr="'sys' is not callable">sys()</warning>

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

# PY-4061
def test_class_assignments():
    class C():
        def __init__(self):
            pass
    d = C
    a = d() #pass
    l = list
    a = l([]) #pass
    d = dict
    b = d({}) #pass
