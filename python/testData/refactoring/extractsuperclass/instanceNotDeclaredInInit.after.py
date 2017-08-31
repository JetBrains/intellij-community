class Parent(object):
    def __init__(self):
        self.eggs = 12


class Child(Parent):
    def foo(self):
        self.eggs = 12
