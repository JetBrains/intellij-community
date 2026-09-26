def foo(x, y):
    s = x + y
    if s > 10:
        print("s>10")
    elif s > 5:
        print("s>5")
    else:
        print("less")
    print("over")


def bar():
    f<caret>oo(1, 2)
