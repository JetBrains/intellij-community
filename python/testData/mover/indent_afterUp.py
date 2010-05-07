class A:
    def foo(self, a, b, c, d):
        if a:
            if b:
                if c:
                    if d:
                        c = 3
                        self.bar()
        a = 2

    def bar(self):
        pass