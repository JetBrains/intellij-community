class A:
    def __init__(self, a):
        pass

class B(A):
    def <warning descr="Call to __init__ of super class is missed">__i<caret>nit__</warning>(self, b, c=1, *args, kw_only):
        pass