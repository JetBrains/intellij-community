class A:
    def __init__(self, a, /, b, *args, c, **kwargs):
        pass

class B(A):
    def <warning descr="Call to __init__ of super class is missed">__in<caret>it__</warning>(self):
        pass