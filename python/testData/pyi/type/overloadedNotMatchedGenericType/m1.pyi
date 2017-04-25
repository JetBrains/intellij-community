from typing import TypeVar, Generic, overload, List, Dict

_T = TypeVar('_T')

class C(Generic[_T]):
    @overload
    def foo(self, i: int) -> Dict[str, _T]: ...
    @overload
    def foo(self, s: str) -> List[_T]: ...
