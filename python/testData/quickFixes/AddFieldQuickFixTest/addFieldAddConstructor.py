class A:
    def __init__(self, a, b):
        self.a = a
        self.b = b

class B(A):
    def foo(self):
        return self.<caret><warning descr="Unresolved attribute reference 'x' for class 'B'">x</warning>
