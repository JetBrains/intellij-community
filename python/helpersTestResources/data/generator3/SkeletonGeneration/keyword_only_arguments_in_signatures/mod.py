def f1(foo, *args, bar):
    pass


def f2(foo, *args, bar, baz=None):
    pass


def f3(foo, *args, baz=None):
    pass


def f4(foo, *, bar):
    pass


def f5(foo, *, bar, baz=None):
    pass


def f6(foo, *, baz=None):
    pass


def f7(*, bar):
    pass


def f8(*, bar, baz=None):
    pass


def f9(*, baz=None):
    pass
