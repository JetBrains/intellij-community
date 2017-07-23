from typing import Callable


def f() -> Callable:
    pass


c = f()
print(c(<arg1>))
