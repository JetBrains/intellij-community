
class A:
    def foo(self):
        print 1

    def baz(self):
        self.foo()
        self.foo()

    def bar(self):
        self.foo()