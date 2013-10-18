class C:
    def baz(self, arg_new):
        self.bar(arg_new)

    def foo(self, option, arg):
        if option:
            self.baz(arg)

    def bar(self, arg):
        pass