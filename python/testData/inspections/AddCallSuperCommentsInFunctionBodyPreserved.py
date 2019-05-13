class A:
    def __init__(self):
        pass


class B(A):
    def <warning descr="Call to __init__ of super class is missed">__in<caret>it__</warning>(self):
        # comment #1
        pass
        # comment #2
        print 42