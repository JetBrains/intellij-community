from typing import Callable


def f() -> Callable[..., int]:
    pass


c = f()
print(c(<arg1>))
