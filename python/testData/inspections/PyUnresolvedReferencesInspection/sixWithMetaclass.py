import six


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


from six import with_metaclass as w_m


class D(w_m(M, B1, B2)):
    def foo(self):
        self.bar()
        D.baz()