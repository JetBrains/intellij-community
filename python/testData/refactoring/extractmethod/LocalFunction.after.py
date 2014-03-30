def bar(f_new):
    return f_new(1)


def foo():
    def f(x):
        return x

    return bar(f)