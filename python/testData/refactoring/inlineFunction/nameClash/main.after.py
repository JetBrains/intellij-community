def foo(arg):
    local = 1
    if arg:
        another = 2
    else:
        another = 3
    return local


def bar():
    x = 1
    local = 2
    another = 3
    local1 = 1
    if x:
        another1 = 2
    else:
        another1 = 3
    res = local1
