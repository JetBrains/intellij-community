class C(object):
    def __new__(cls):
        self = object.__new__(cls)
        self.foo = 1
        return self

x = C()
print(x.foo)
print(x.<warning descr="Unresolved attribute reference 'bar' for class 'C'">bar</warning>)
