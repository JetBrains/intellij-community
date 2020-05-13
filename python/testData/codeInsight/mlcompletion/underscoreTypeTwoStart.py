class MyClass(object):
    def __init__(self):
        self.__private_var = 42
        self._private_var = 11
        self.instance_var = 12

    def foo(self):
        self.<caret>


obj = MyClass()