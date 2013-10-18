class B(object):
    __slots__ = ['foo']

b = B()
b.<warning descr="'B' object has no attribute 'bar'">bar</warning> = 1

class C(B):
    pass

c = C()
c.bar = 1

def test_slots_with_dict():
    class C(object):
        __slots__ = ['__local', '__name__', '__dict__']
    a = C()
    a.foo = 1 #pass
