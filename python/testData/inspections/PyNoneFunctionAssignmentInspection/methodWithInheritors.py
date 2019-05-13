class C(object):
    def foo(self):
        pass

    def bar(self):
        pass

    def test(self):
        x = self.foo()
        <weak_warning descr="Function 'bar' doesn't return anything">y = self.bar()</weak_warning>


class D(C):
    def foo(self):
        return 2
