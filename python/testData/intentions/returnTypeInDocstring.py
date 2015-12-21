def f(x):
    pass


def g(x):
    y = x
    f(x)  # (1)
    f(<caret>y)  # (2)