from _typeshed import IdentityFunction
from collections.abc import Callable, Sequence
from logging import Logger
from typing import Any, TypeVar

_R = TypeVar("_R")

def retry_call(
    f: Callable[..., _R],
    fargs: Sequence[Any] | None = ...,
    fkwargs: dict[str, Any] | None = ...,
    exceptions: type[Exception] | tuple[type[Exception], ...] = ...,
    tries: int = ...,
    delay: float = ...,
    max_delay: float | None = ...,
    backoff: float = ...,
    jitter: tuple[float, float] | float = ...,
    logger: Logger | None = ...,
) -> _R: ...
def retry(
    exceptions: type[Exception] | tuple[type[Exception], ...] = ...,
    tries: int = ...,
    delay: float = ...,
    max_delay: float | None = ...,
    backoff: float = ...,
    jitter: tuple[float, float] | float = ...,
    logger: Logger | None = ...,
) -> IdentityFunction: ...
