def foo():
    i<caret>f a + 2 > 3 and b < 4:
    #comment
        a = a and b
        b = 4
    elif a > 20:
        pass
    elif b > 20:
        b = a + 2
        foo()
    else:
        b = a and b
        a = 4