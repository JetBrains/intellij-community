def foo(x):
    bar()
    print(1)


def bar():
    try:
        pass
    except Exception:
        raise