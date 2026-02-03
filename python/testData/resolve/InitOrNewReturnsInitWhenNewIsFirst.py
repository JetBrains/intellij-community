class MyClass(object):
    def __new__(cls):
        return object.__new__(cls)

    def __init__(self):
        self.x = 42
