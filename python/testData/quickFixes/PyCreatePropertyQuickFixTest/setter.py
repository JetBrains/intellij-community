
class C(object):
    def __init__(self):
        self._x = None

    @property
    def x(self):
        """I'm the 'x' property."""
        return self._x

c = C()
print(c.x)
del <warning descr="Property 'x' cannot be deleted">c.x</warning>

<warning descr="Property 'x' cannot be set">c.<caret>x</warning> = 1