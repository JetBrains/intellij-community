from typing import Any, ContextManager, Text, TypeVar, overload
from typing_extensions import Literal

from .client import Pipeline, Redis, _StrType

_T = TypeVar("_T")

HIREDIS_AVAILABLE: bool

@overload
def from_url(url: Text, db: int | None = ..., *, decode_responses: Literal[True], **kwargs: Any) -> Redis[str]: ...
@overload
def from_url(url: Text, db: int | None = ..., *, decode_responses: Literal[False] = ..., **kwargs: Any) -> Redis[bytes]: ...
@overload
def str_if_bytes(value: bytes) -> str: ...  # type: ignore
@overload
def str_if_bytes(value: _T) -> _T: ...
def safe_str(value: object) -> str: ...
def pipeline(redis_obj: Redis[_StrType]) -> ContextManager[Pipeline[_StrType]]: ...

class dummy: ...
