class Foo:
    def xyzzy(self): pass

def x(p):
    if isinstance(p, Foo):
        p.xyzzy()
