def bar():
    def f(x):
        return x
    return f(1)


def foo():
    return bar()
