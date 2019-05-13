class C:
    def foo(self):
        return self.bar()

    def bar(self):
        if (self.cond):
            return 1
        else:
            return 2