from typing import Callable

class A:
    pass

B = Callable
assert issubclass(A, <error descr="Parameterized generics cannot be used with instance and class checks">B<caret>[..., str]</error>)