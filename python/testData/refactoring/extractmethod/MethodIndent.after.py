class Foo(X, Y, Z):
    def __init__(self):
        for base in self__class__.__bases__:
            self.bar(base)

    def bar(self, base_new):
        try:
            base_new.__init__(self)
        except AttributeError:
            pass
