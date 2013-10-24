class C(object):
    def __init__(self):
        self._x = None

    @property
    def bar(self):
        """I'm the 'x' property."""
        return self._x

    @bar.setter
    def bar(self, value):
        self._x = value

    @bar.deleter
    def bar(self):
        del self._x
