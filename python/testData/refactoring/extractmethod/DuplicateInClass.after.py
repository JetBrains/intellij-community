
class A:
    def baz(self):
        self.foo()
        self.foo()

    def foo(self):
        print 1

    def bar(self):
        self.foo()