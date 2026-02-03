class C(object):
    def __init__(self, value):
        self.x = value

    def getx(self):
        return self._x

    def setx(self, value):
        self._x = value    # False positive for self._x

    x = property(getx, setx, doc="The 'x' property.")
