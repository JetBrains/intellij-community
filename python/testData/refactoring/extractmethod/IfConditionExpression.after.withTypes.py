from typing import Any


def foo(some_var):
    if bar(some_var):
        print('w00t')


def bar(some_var_new) -> Any:
    return some_var_new