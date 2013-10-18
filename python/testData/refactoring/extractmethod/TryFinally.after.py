def bar():
    try:
        print(1)
    finally:
        pass


def foo(x):
    bar()
    return x
