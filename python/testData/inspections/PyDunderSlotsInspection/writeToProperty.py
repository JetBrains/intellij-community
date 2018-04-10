class Test(object):
    __slots__ = ('_x',)

    def __init__(self):
        self._x = 1

    @property
    def x(self):
        return self._x

    @x.setter
    def x(self, value):
        self._x = value


Test().x = 2