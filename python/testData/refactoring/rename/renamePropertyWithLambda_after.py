class C(object):
    bar = property(lambda self: 'bar')

c = C()
print(c.bar, c.bar)
