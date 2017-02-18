import <error descr="No module named six">six</error>


class M(type):
    def baz(self):
        pass


class B1(object):
    pass


class B2(object):
    def bar(self):
        pass


class C(six.with_metaclass(M, B1, B2)):
    def foo(self):
        self.bar()
        C.baz()


from <error descr="Unresolved reference 'six'">six</error> import <error descr="Unresolved reference 'with_metaclass'">with_metaclass</error> as w_m


class D(w_m(M, B1, B2)):
    def foo(self):
        self.bar()
        D.baz()