def foo():
    if <caret>a + 2 > 3:
        if b < 4:
            a = a and b
            b = 4
    else: # this prevents ifs from joining
        print a
