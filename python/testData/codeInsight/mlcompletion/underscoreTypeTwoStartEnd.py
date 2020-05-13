class MyClass(object):
    def __init__(self):
        self.__private_var = 42
        self._priv = 11
        self.instance_var = 12

    def foo(self):
        self.__private_var = 22


obj = MyClass()
obj.<caret>