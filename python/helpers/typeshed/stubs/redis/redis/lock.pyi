from _typeshed import Self
from types import TracebackType
from typing import Any, ClassVar, Protocol

from redis.client import Redis

class _Local(Protocol):
    token: str | bytes | None

class Lock:
    LUA_EXTEND_SCRIPT: ClassVar[str]
    LUA_REACQUIRE_SCRIPT: ClassVar[str]
    LUA_RELEASE_SCRIPT: ClassVar[str]
    lua_extend: ClassVar[Any | None]
    lua_reacquire: ClassVar[Any | None]
    lua_release: ClassVar[Any | None]
    local: _Local
    def __init__(
        self,
        redis: Redis[Any],
        name: str,
        timeout: float | None = ...,
        sleep: float = ...,
        blocking: bool = ...,
        blocking_timeout: float | None = ...,
        thread_local: bool = ...,
    ) -> None: ...
    def register_scripts(self) -> None: ...
    def __enter__(self: Self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_value: BaseException | None, traceback: TracebackType | None
    ) -> bool | None: ...
    def acquire(
        self,
        sleep: float | None = ...,
        blocking: bool | None = ...,
        blocking_timeout: float | None = ...,
        token: str | bytes | None = ...,
    ) -> bool: ...
    def do_acquire(self, token: str | bytes) -> bool: ...
    def locked(self) -> bool: ...
    def owned(self) -> bool: ...
    def release(self) -> None: ...
    def do_release(self, expected_token: str | bytes) -> None: ...
    def extend(self, additional_time: float, replace_ttl: bool = ...) -> bool: ...
    def do_extend(self, additional_time: float, replace_ttl: bool) -> bool: ...
    def reacquire(self) -> bool: ...
    def do_reacquire(self) -> bool: ...
