class A:
    def __init__(self, a):
        pass


class B(A):
    def __init__(self, a=1):
        A.__init__(self, a)