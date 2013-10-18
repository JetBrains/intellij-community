class A:
    def foo(self):
        if (self.is_good):
            y = self.xxx
        else:
            y = "str"
        return y
     def bar(self):
        self.foo().count()
