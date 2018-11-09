from typing import Callable

class A:
    pass

assert isinstance(A(), Callable)