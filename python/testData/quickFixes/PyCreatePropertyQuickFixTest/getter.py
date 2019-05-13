
class C(object):
    def __init__(self):
        self._x = None

    @x.setter
    def x(self, value):
        self._x = value

c = C()
print(<warning descr="Property 'x' cannot be read">c.<caret>x</warning>)
del <warning descr="Property 'x' cannot be deleted">c.x</warning>

c.x = 1