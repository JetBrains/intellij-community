class C:
    def g(self):
        self.foo = 0

    def __init__(self):
        self.g()
        print(self.foo)  # -> self.foo in g
        #          <ref>
        self.foo = 1
