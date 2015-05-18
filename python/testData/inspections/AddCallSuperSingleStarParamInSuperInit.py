class A:
    def __init__(self, *, kw_only, optional_kw_only=None):
        pass

class B(A):
    def <warning descr="Call to __init__ of super class is missed">__i<caret>nit__</warning>(self):
        pass