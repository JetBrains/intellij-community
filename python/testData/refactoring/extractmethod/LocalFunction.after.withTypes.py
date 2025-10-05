from typing import Any, Callable


def foo():
    def f(x):
        return x

    return bar(f)


def bar(f_new: Callable[..., Any]) -> Any:
    return f_new(1)