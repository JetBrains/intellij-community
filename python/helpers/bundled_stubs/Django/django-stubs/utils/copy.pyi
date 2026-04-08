from typing import Any, Protocol, TypeVar, type_check_only

_RT_co = TypeVar("_RT_co", covariant=True)

@type_check_only
class _SupportsReplace(Protocol[_RT_co]):
    def __replace__(self, /, *_: Any, **changes: Any) -> _RT_co: ...

def replace(obj: _SupportsReplace[_RT_co], /, **changes: Any) -> _RT_co: ...
