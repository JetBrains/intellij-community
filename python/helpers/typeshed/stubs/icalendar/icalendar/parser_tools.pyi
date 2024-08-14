from typing import Any, Final, TypeVar, overload

_T = TypeVar("_T")

SEQUENCE_TYPES: Final[tuple[type[Any], ...]]
DEFAULT_ENCODING: str

def from_unicode(value: str | bytes, encoding: str = "utf-8") -> bytes: ...
def to_unicode(value: str | bytes, encoding: str = "utf-8") -> str: ...
@overload
def data_encode(data: str, encoding: str = ...) -> bytes: ...  # type: ignore[misc]
@overload
def data_encode(data: dict[Any, Any], encoding: str = ...) -> dict[Any, Any]: ...
@overload
def data_encode(data: list[Any] | tuple[Any, ...], encoding: str = ...) -> list[Any]: ...  # type: ignore[misc]
@overload
def data_encode(data: _T, encoding: str = ...) -> _T: ...
