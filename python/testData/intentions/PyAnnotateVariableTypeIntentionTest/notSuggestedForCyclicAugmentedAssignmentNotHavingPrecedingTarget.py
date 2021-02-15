def func(p):
    while p:
        x += 1
        if p:
            <caret>x += 2
    x = 0
    return x
