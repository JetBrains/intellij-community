class C(object):
    __slots__ = ['__local', '__name__', '__dict__']

a = C()
a.foo = 1 #pass