class C(object):
    def f(self, name):
        return name

    __getattr__ = f

c = C()
print(c.foo) #pass