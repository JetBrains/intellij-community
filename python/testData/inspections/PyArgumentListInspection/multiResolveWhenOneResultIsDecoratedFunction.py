class C1:
    def foo(self, x):
        return self


class C2:
    @decorated
    def foo(self, x, y):
        return self


def f():
    """
    :rtype: C1 | C2
    """
    pass


f().foo(<warning descr="No overload of 'foo' matches the arguments. Argument types: (). Expected one of: (x), (x, y)">)</warning>
f().foo(1)
f().foo(1, 2)
f().foo<warning descr="No overload of 'foo' matches the arguments. Argument types: (Literal[1], Literal[2], Literal[3]). Expected one of: (x), (x, y)">(1, 2, 3)</warning>
