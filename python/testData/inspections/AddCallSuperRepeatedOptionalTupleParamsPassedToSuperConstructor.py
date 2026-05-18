class A:
    def __init__(self, x, (y, z)=(1, (2, 3)), (a, b)=(1, 2)):
        pass


class B(A):
    def <warning descr="Call to '__init__' of super class is missing">__i<caret>nit__</warning>(self, y, z, b):
        pass