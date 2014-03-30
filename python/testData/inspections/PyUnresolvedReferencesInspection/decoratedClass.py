class C(object):
    def foo(self):
        self.bar = 1

    baz = 2

    @property
    def quux(self):
        return 3

def decorator(c):
    return c

@decorator
class D(object):
    foo = 1

c = C()
print(c.bar, c.baz, c.quux)
print(c.<warning descr="Unresolved attribute reference 'spam' for class 'C'">spam</warning>) #fail
d = D()
print(d.foo)
print(d.eggs) #pass
