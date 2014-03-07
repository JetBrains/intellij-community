
class C(object):
    def __init__(self):
        self._x = None

    @property
    def x(self):
        """I'm the 'x' property."""
        return self._x

    @x.deleter
    def x(self):
        del self._x


c = C()
print(c.x)
del c.x

c.x = 1