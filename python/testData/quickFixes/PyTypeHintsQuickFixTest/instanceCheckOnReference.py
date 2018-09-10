from typing import Callable

class A:
    pass

C = Callable[..., str]
assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks"><caret>C</error>)