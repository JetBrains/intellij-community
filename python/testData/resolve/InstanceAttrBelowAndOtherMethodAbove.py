class C:
    def g(self):
        self.foo = 0

    def h(self):
        self.foo = 0

    def f(self):
        x = self.foo
        #        <ref>
        self.foo = 1
        return x
