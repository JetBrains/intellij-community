def f():
    a = do_smth()
    b1 = <caret>do_smth_with(a)
    a = do_smth()
    b = do_smth_with(a + 1)
    do_smth_with(b1, b)
