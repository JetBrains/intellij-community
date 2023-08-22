def foo(a, /, b, *, c):
    print(a, b, c)


def egg():
    a = 1
    b = 2
    c = 3
    foo(<caret>