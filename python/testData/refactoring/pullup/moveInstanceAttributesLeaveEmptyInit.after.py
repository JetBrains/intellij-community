class Parent(object):
    C = 12

    def __init__(self):
        self.foo = 12

    def foo(self):
        pass


class Child(Parent, object):
    def __init__(self):
        pass

    AC = 11

    def foo(self):
        pass


class Bar(object):
    pass