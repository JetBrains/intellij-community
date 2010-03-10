a = 1

def foo():
    global a
    print a
    a = 2
    print a

foo()
