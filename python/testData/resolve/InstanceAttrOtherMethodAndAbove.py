class C:
    def g(self):
        self.foo = 0

    def f(self):
        self.foo = 1
        return self.foo
        #           <ref>
