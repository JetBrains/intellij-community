def foo(c, x, y):
    if c:
        z = x
    else:
        z = ''
    y = z + y  # pass
    return y
