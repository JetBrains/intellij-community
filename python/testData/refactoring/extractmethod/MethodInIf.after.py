class C:
    def foo(self, option, arg):
        if option:
            self.baz(arg)

    def baz(self, arg_new):
        self.bar(arg_new)

    def bar(self, arg):
        pass