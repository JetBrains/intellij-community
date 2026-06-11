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


f().foo<warning descr="No overload of 'foo' matches the arguments. Argument types: (int, int, int). Expected one of: (x), (x, y)">(1, 2, 3)</warning>
