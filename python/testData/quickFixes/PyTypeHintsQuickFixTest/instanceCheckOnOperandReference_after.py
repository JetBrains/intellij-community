from typing import Callable

class A:
    pass

B = Callable
assert issubclass(A, B)