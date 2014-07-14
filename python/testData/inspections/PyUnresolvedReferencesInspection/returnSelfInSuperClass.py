class C(object):
    def get_self(self):
        return self


class D(C):
    def foo(self):
        pass


d = D()
print(d.foo())
print(d.get_self().foo())  # pass
