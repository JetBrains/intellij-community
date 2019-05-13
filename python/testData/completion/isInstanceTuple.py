class Foo:
    def test(self): pass

class Foo2:
    def test(self): pass

def x(p):
    if isinstance(p, (Foo, Foo2)):
        p.te<caret>
