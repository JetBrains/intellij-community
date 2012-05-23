def foo():
    x = 1

    def baz():
        nonlocal x
        x = 2

    def bar():
        nonlocal x
        baz()
        print(x)
    bar()
foo()