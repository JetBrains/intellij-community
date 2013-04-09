
class Test(object):
    def __new__(cls, foo):  # False positive
        self = super(Test, cls).__new__(cls)
        self.foo = foo
        return self

    def bar(self):
        return self.foo


t = Test(42)
print(t.bar())