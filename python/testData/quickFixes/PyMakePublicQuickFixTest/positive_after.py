class A:
    def __init__(self):
        self.x = 1

    def _foo(self):
        print(self.x)

a = A()
a._foo()

print(a.x)