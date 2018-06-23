class C(object):
    __slots__ = ['__local', '__name__', '__dict__']

a = C()
print(a.<warning descr="Unresolved attribute reference 'bar' for class 'C'">bar</warning>)
a.foo = 1 #pass