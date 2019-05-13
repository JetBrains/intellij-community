class A:
    def __init__(self, x):
        self.x = x


class B(A):
    def <warning descr="Call to __init__ of super class is missed">__init_<caret>_</warning>(this:'B', y):
        this.y = y