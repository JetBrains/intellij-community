class A():
    def __init__(self):
        a = 1

class C(A):
    def __init__(self):
        A.__init__(self)

    def foo(self):
        pass