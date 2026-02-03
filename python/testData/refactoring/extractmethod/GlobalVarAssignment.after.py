x = 0

def foo():
    global x
    bar()


def bar():
    global x
    x = 1