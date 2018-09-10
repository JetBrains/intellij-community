class MyClass:
    def __init__(self):
        pass

    def method(self, x):
        # type: (object) -> object
        pass


x = MyClass()
foo = x.method(42)
