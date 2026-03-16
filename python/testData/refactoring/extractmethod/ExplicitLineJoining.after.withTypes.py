from typing import Any


def foo(num):
    if bar(num):
        return 1


def bar(num_new) -> Any:
    return num_new >= 0 \
        and num_new <= 9