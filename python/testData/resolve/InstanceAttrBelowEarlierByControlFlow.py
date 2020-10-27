class C:
    def f(self):
        c = False
        while True:
            if c:
                return self.foo
                #           <ref>
            else:
                c = True
                self.foo = 1
