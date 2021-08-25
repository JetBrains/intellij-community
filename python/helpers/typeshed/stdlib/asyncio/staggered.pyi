import sys
from typing import Any, Awaitable, Callable, Iterable, Tuple

from . import events

if sys.version_info >= (3, 8):
    async def staggered_race(
        coro_fns: Iterable[Callable[[], Awaitable[Any]]], delay: float | None, *, loop: events.AbstractEventLoop | None = ...
    ) -> Tuple[Any, int | None, list[Exception | None]]: ...
