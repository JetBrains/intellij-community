class B:
    def __init__(self):
        pass

    def __new__(self):
        pass

    def foo(self, a):
        pass


class C(B):
    def __init__(self, a): # different but ok because __init__ is special
        pass

    def __new__(self, p, q): # different but ok because __new__ is special
        pass

    def foo<warning descr="Signature of method 'C.foo()' does not match signature of base method in class 'B'">(self, s, t)</warning>:
        pass
