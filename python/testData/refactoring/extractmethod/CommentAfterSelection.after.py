def bar(c_new):
    if c_new:
        print(1)


def foo(c):
    x = 1
    bar(c)
    # Comment
    return x