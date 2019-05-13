def f(a, b, c):
    if b:
        pass
    elif a:
        c = <selection>1
        if b:
            a = 3
        else:
            b<caret></selection> = 4
    else:
        pass
