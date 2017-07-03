class P(object):
    def foo(self):
        return "str"


class A(object):
    _foo = P.foo

    def bar(self):
        return self._foo(P())