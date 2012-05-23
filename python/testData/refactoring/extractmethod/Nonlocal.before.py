def foo():
    x = 1
    def bar():
        nonlocal x
        <selection>x = 2</selection>
        print(x)
    bar()
foo()