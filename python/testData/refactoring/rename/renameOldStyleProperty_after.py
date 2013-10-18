class C(object):
    def __init__(self):
        self._foo = 'foo'

    def get_foo(self):
        return self._foo

    def set_foo(self, value):
        self._foo = value

    def __str__(self):
        return self.bar

    bar = property(get_foo, set_foo)


c = C()
print(c.bar)
