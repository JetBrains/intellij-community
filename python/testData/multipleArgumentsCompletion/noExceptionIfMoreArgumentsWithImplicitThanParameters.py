class C:
    def foo(self, x, y):
        pass

    def bar(self):
        x = 22
        y = 33
        z = 44
        self.foo(x, y, z, <caret>)