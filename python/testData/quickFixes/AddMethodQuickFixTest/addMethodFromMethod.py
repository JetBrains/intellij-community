class A:
    def __init__(self):
        self.x = 1

    def foo(self, a):
        self.<caret><warning descr="Unresolved attribute reference 'y' for class 'A'">y</warning>(1, a)

# Some comment

class B:
    pass
