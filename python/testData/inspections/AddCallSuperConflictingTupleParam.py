class A:
    def __init__(self, (a, (b, c)), (d, e)):
        pass

class B(A):
    def <warning descr="Call to __init__ of super class is missed">__init_<caret>_</warning>(self, (a, b), c, e):
        pass