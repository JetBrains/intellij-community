class C(object):
    @property
    def f(self):
        return lambda x, y: (x, y)


c = C()
c.f(1, 2)
c.f(<warning descr="Parameter 'x' unfilled"><warning descr="Parameter 'y' unfilled">)</warning></warning>
c.f(1, 2, <warning descr="Unexpected argument">3</warning>)
