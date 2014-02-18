class Parent(object):
    def __init__(self):
        self.foo = 12


class Child(Parent):
    def foo(self):
        self.foo = 12
