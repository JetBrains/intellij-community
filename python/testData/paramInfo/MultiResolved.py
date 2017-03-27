class C1:
    def foo(self, x):
        return self


class C2:
    def foo(self, x, y: str):
        return self


def f():
    """
    :rtype: C1 | C2
    """
    pass


f().foo(1, <arg2>2)
