def bar():
    try:
        pass
    except Exception:
        raise


def foo(x):
    bar()
    print(1)