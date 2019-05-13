class C1:
    def foo(self, x):
        return self


class C2:
    def foo(self, x, y):
        return self


def f():
    """
    :rtype: C1 | C2
    """
    pass


f().foo(<warning descr="Parameter(s) unfilledPossible callees:C1.foo(self: C1, x)C2.foo(self: C2, x, y)">)</warning>
