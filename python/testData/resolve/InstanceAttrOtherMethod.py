class C:
    def f(self):
        return self.foo
    #               <ref>

    def g(self):
        self.foo = 1
