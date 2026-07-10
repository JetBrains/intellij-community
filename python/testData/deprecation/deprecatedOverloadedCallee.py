from typing import overload
from warnings import warn

from typing_extensions import deprecated

@overload
def f(x: int) -> int: ...

@overload
@deprecated("f is deprecated")  # overload is deprecated
def f(x: str) -> str: ...

def f(x):
    return x

<warning descr="f is deprecated">f</warning>('x')
f(1)