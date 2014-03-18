
class C(object):
    @property
    def x(self):
        return self._x

    def __init__(self):
        self._x = None

    @x.setter
    def x(self, value):
        self._x = value

c = C()
print(c.x)
del c.x

c.x = 1