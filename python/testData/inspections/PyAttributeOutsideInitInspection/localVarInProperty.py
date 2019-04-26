class MyClass(object):
    def __init__(self, value):
        self.x = value

    @property
    def x(self):
        return self._x

    @x.setter
    def x(self, value):
        _x = 42  # unrelated assignment

    def function(self):
        <weak_warning descr="Instance attribute _x defined outside __init__">self._x</weak_warning> = 42
