from typing import Callable


def f() -> Callable[[int, str], int]:
    pass


c = f()
print(c(<arg1>))
