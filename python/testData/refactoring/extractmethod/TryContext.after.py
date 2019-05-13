def foo(f):
    x = 1
    x = bar(f, x)
    return x


def bar(f_new, x_new):
    try:
        x_new = f_new()
    except Exception:
        pass
    return x_new