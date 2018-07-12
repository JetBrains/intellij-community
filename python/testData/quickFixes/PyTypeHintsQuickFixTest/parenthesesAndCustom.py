from typing import Generic, TypeVar

T = TypeVar("T")

class A(Generic[T]):
    def __init__(self, v):
        pass

def c(d):
    # type: (<warning descr="Generics should be specified through square brackets">A<caret>(int)</warning>) -> None
    pass