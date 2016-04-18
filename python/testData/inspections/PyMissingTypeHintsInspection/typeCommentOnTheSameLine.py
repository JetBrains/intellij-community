def foo(x):  # type: (int) -> int
    pass


def bar(x)<error descr="':' expected"> </error> # type: (int) -> int
    pass


def baz(
        x
):  # type: (int) -> int
    pass
