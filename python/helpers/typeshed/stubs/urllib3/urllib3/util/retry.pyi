import logging
from _typeshed import Self
from collections.abc import Collection
from types import TracebackType
from typing import Any, ClassVar, NamedTuple
from typing_extensions import Literal

from .. import exceptions
from ..connectionpool import ConnectionPool
from ..response import HTTPResponse

ConnectTimeoutError = exceptions.ConnectTimeoutError
MaxRetryError = exceptions.MaxRetryError
ProtocolError = exceptions.ProtocolError
ReadTimeoutError = exceptions.ReadTimeoutError
ResponseError = exceptions.ResponseError

log: logging.Logger

class RequestHistory(NamedTuple):
    method: str | None
    url: str | None
    error: Exception | None
    status: int | None
    redirect_location: str | None

class Retry:
    DEFAULT_ALLOWED_METHODS: ClassVar[frozenset[str]]
    RETRY_AFTER_STATUS_CODES: ClassVar[frozenset[int]]
    DEFAULT_REMOVE_HEADERS_ON_REDIRECT: ClassVar[frozenset[str]]
    DEFAULT_BACKOFF_MAX: ClassVar[int]

    total: bool | int | None
    connect: int | None
    read: int | None
    redirect: Literal[True] | int | None
    status: int | None
    other: int | None
    allowed_methods: Collection[str] | Literal[False] | None
    status_forcelist: Collection[int]
    backoff_factor: float
    raise_on_redirect: bool
    raise_on_status: bool
    history: tuple[RequestHistory, ...]
    respect_retry_after_header: bool
    remove_headers_on_redirect: frozenset[str]
    def __init__(
        self,
        total: bool | int | None = ...,
        connect: int | None = ...,
        read: int | None = ...,
        redirect: bool | int | None = ...,
        status: int | None = ...,
        other: int | None = ...,
        allowed_methods: Collection[str] | Literal[False] | None = ...,
        status_forcelist: Collection[int] | None = ...,
        backoff_factor: float = ...,
        raise_on_redirect: bool = ...,
        raise_on_status: bool = ...,
        history: tuple[RequestHistory, ...] | None = ...,
        respect_retry_after_header: bool = ...,
        remove_headers_on_redirect: Collection[str] = ...,
        method_whitelist: Collection[str] | None = ...,
    ) -> None: ...
    def new(self: Self, **kw: Any) -> Self: ...
    @classmethod
    def from_int(
        cls, retries: Retry | bool | int | None, redirect: bool | int | None = ..., default: Retry | bool | int | None = ...
    ) -> Retry: ...
    def get_backoff_time(self) -> float: ...
    def parse_retry_after(self, retry_after: str) -> float: ...
    def get_retry_after(self, response: HTTPResponse) -> float | None: ...
    def sleep_for_retry(self, response: HTTPResponse | None = ...) -> bool: ...
    def sleep(self, response: HTTPResponse | None = ...) -> None: ...
    def is_retry(self, method: str, status_code: int, has_retry_after: bool = ...) -> bool: ...
    def is_exhausted(self) -> bool: ...
    def increment(
        self,
        method: str | None = ...,
        url: str | None = ...,
        response: HTTPResponse | None = ...,
        error: Exception | None = ...,
        _pool: ConnectionPool | None = ...,
        _stacktrace: TracebackType | None = ...,
    ) -> Retry: ...
