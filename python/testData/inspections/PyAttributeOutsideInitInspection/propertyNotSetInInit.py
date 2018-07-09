class C(object):
    def __init__(self, value):
        pass

    def getx(self):
        return self._x

    def setx(self, value):
        <weak_warning descr="Instance attribute _x defined outside __init__">self._x</weak_warning> = value

    x = property(getx, setx, doc="The 'x' property.")
