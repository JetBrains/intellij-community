class MyClass(object):
    def __init__(self):
        self.x = 42

    def __new__(cls):
        return object.__new__(cls)
