class C:
    def f(self, c):
        if c:
            return self.foo
            #           <ref>
        else:
            self.foo = 1
