from typing import TypeVar, Generic, overload, List

_T = TypeVar('_T')

class C(Generic[_T]):
    @overload
    def foo(self, i: int) -> _T: ...
    @overload
    def foo(self, s: slice) -> List[_T]: ...
