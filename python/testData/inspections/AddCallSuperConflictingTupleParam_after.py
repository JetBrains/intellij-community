class A:
    def __init__(self, (a, (b, c)), (d, e)):
        pass

class B(A):
    def __init__(self, (a, b), c, e, d):
        A.__init__(self, (a, (b, c)), (d, e))