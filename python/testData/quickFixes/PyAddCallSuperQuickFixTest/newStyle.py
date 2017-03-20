
class A(object):
    def __init__(self):
        a = 1

class C(A):
    def <warning descr="Call to __init__ of super class is missed">__i<caret>nit__</warning>(self):
        pass

    def foo(self):
        pass