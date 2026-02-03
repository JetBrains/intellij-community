import contextlib


@contextlib.contextmanager
def manager():
    yield 1, 2


def func():
    with manager() as (_, b):
        print(42)