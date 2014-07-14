class A:
    @property
    def x(self):
        return self._x

    def __init__(self):
        self._x = 1

    def _foo(self):
        print(self._x)

a = A()
a._foo()

print(a.x)
print(a.<caret>_x)