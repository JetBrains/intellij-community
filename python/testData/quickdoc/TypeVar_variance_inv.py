from typing import TypeVar, Generic

TT = TypeVar('TT', infer_variance=True)

class C(Generic[TT]):
    def f(self, p: T<the_ref>T) -> TT:
        pass
