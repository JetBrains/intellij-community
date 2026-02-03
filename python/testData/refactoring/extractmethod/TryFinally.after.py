def foo(x):
    bar()
    return x


def bar():
    try:
        print(1)
    finally:
        pass
