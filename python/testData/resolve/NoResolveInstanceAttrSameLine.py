class C:
    def f(self):
        self.foo = [1, 2, self.foo]
        #                      <ref>
