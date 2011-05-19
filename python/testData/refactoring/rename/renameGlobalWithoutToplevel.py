def f1():
    global foo
    foo = [1, 2, 3]

def f2(x):
    global foo
    foo = foo + [x]
    if 1 in foo:
        return f<caret>oo

def f3(x):
    return foo + [x]

def f4(x):
    global foo
    return foo + [x]

def f5(foo):
    return foo