class A:
    def __init__(self):
        self._x = 1

    def _foo(self):
        print(self._x)

a = A()
a._foo()

print(a.<caret>_x)