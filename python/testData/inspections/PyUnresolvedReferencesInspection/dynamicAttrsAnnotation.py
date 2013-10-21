class B(object):
    def foo(self):
        pass


class C(object):
    """
    @DynamicAttrs
    """
    def bar(self):
        pass


class D(C):
    def baz(self):
        pass


b = B()
b.foo(), b.<warning descr="Unresolved attribute reference 'spam' for class 'B'">spam</warning>()

c = C()
c.bar(), c.spam()

d = D()
d.bar(), d.baz(), d.spam(), d.eggs()
