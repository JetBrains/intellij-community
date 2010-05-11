class A:
    def foo(self, a, b, c, d):
        if a:
            if b:
                if c:
                    if d:
                        self.bar()
        if c:
            a = 2
            c = 3

    def bar(self):
        pass