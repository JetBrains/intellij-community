class C:
    def __init__(self):
        self.foo = 42
        self.bar = 'spam'


def method(foo, bar, x):
    print(foo, bar)


foo = C()
method(foo.foo, foo.bar, 1)