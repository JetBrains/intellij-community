class Foo:
    def xyzzy(self): pass

def bar(): pass

f = bar()
assert isinstance(f, Foo)
f.xy<caret>