from _typeshed import Self
from collections.abc import Callable
from types import TracebackType
from typing import Any

def connection_memoize(key: str) -> Callable[..., Any]: ...

class TransactionalContext:
    def __enter__(self: Self) -> Self: ...
    def __exit__(
        self, type_: type[BaseException] | None, value: BaseException | None, traceback: TracebackType | None
    ) -> None: ...
