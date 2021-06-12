class C:
    def f(self):
        self.foo = 1
        if self.foo:
            #   <ref>
            self.foo = 0
