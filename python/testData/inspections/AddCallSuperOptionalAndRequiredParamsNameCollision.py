class A:
    def __init__(self, a):
        pass


class B(A):
    def <warning descr="Call to __init__ of super class is missed">__init_<caret>_</warning>(self, a=1):
        pass