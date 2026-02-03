import contextlib


@contextlib.contextmanager
def manager():
    yield 1, 2


def func():
    with manager() as (<caret>a, b):
        print(42)