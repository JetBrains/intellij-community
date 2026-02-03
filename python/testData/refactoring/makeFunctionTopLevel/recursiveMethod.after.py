class C:
    def __init__(self, foo):
        self.foo = foo


def method(foo1, foo):
    method(foo1, foo1)
    method(C(1).foo, 2)
