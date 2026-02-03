class A:
    def __init__(self, a, b=2, c=3):
        self.a = a


class B(A):
    def <warning descr="Call to __init__ of super class is missed">_<caret>_init__</warning>(self, a, c):
        pass
