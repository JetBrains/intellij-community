a = 1

def foo():
    global b, a
    print a
    a = 2
    print a

foo()
