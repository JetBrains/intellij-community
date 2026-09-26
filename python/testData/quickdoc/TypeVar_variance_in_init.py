from typing import TypeVar, Generic

T = TypeVar('T', infer_variance=True)

class C(Generic[T]):
    def __init__(self, t: T<the_ref>):
        pass
    def method(self) -> T:
        pass
