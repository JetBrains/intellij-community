def f1():
    global bar
    bar = [1, 2, 3]

def f2(x):
    global bar
    bar = bar + [x]
    if 1 in bar:
        return bar

def f3(x):
    return bar + [x]

def f4(x):
    global bar
    return bar + [x]

def f5(foo):
    return foo