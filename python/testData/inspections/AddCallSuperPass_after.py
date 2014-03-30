class A:
    def __init__(self, c, a = 5):
        pass

class B(A):
    def __init__(self, r, c, b=6):
        A.__init__(self, c)
