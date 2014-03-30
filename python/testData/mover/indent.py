class A:
    def foo(self, a, b, c, d):
        if a:
            if b:
                if c:
                    if d:
                        self.bar()
                c<caret> = 3
        if c:
            a = 2

    def bar(self):
        pass