class B:
    def g(self):
        self.foo = 0


class C(B):
    def f(self):
        x = self.foo
        #        <ref>
        self.foo = 1
        return x
