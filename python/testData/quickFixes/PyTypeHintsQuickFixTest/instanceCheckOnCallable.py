from typing import Callable

class A:
    pass

assert isinstance(A(), <error descr="Parameterized generics cannot be used with instance and class checks">Callable<caret>[..., str]</error>)