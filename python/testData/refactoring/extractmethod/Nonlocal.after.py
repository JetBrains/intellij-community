def foo():
    x = 1
    def bar():
        nonlocal x
        baz()
        print(x)

    def baz():
        nonlocal x
        x = 2

    bar()
foo()