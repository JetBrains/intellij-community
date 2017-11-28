class A(object):
    __slots__ = ('_y',)

    def __init__(self):
        self._y = 1

    @property
    def x(self):
        return self._y

    @x.setter
    def x(self, value):
        self._y = value


class B(A):
    __slots__ = ('_x',)


B().x = 2