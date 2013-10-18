class C:
    def bar(self):
        if (self.cond):
            return 1
        else:
            return 2

    def foo(self):
        return self.bar()