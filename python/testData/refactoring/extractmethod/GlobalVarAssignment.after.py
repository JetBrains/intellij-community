x = 0


def bar():
    global x
    x = 1


def foo():
    global x
    bar()