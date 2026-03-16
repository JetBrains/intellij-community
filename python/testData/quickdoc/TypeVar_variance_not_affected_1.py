from typing import TypeVar, Generic

TT = TypeVar('TT', infer_variance=True)

class C(Generic[TT]):
    def method(self) -> TT:
        pass

def f() -> T<the_ref>T: