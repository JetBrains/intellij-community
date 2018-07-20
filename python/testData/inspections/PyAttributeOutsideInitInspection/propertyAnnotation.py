class C(object):
    def __init__(self):
        self.x = None

    @property
    def x(self):
        return self._x

    @x.setter
    def x(self, value):
        self._x = value  # False positive for self._x
