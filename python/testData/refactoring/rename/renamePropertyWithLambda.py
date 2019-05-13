class C(object):
    foo = property(lambda self: 'bar')

c = C()
print(c.f<caret>oo, c.foo)
