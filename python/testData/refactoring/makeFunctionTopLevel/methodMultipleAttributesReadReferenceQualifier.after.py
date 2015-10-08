class C:
    def __init__(self):
        self.foo = 42
        self.bar = 'spam'


def method(foo, bar, x):
    print(foo, bar)


inst = C()
method(inst.foo, inst.bar, 1)