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
    res = fo<caret>o(x)
