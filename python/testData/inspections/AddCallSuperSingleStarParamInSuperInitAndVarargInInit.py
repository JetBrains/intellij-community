class A:
    def __init__(self, *, kw_only):
        pass

class B(A):
    def <warning descr="Call to __init__ of super class is missed">__i<caret>nit__</warning>(self, *args, another_kw_only):
        pass