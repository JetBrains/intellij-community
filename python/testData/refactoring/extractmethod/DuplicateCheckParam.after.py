def f():
    a = do_smth()
    b1 = foo(a)
    a = do_smth()
    b = foo(a + 1)
    do_smth_with(b1, b)


def foo(a_new):
    b1 = do_smth_with(a_new)
    return b1
