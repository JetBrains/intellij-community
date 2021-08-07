class B:
    def g(self):
        self.foo = 0


class C(B):
    def f(self):
        self.foo = 1
        return self.foo
        #           <ref>
