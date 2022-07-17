# This only exists in 3.11+. See VERSIONS.

from _typeshed import Self
from types import TracebackType
from typing import Any, Coroutine, Generator, TypeVar

from .tasks import Task

__all__ = ["TaskGroup"]

_T = TypeVar("_T")

class TaskGroup:
    def __init__(self) -> None: ...
    async def __aenter__(self: Self) -> Self: ...
    async def __aexit__(self, et: type[BaseException] | None, exc: BaseException | None, tb: TracebackType | None) -> None: ...
    def create_task(self, coro: Generator[Any, None, _T] | Coroutine[Any, Any, _T], *, name: str | None = ...) -> Task[_T]: ...
