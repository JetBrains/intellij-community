def f():
    x = 1
    def g():
        global x
        nonlocal x
