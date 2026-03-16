from typing import TypeVar, Generic

TT = TypeVar('TT', covariant=True)

class C(Generic[TT]):
    def f() -> T<the_ref>T: ...