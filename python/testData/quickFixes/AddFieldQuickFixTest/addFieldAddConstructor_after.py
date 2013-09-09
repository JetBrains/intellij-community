class A:
    def __init__(self, a, b):
        self.a = a
        self.b = b

class B(A):
    def __init__(self, a, b):
        A.__init__(self, a, b)
        self.x = None

    def foo(self):
        return self.x
