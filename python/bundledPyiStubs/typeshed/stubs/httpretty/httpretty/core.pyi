import re
from collections.abc import Callable, Iterable, Mapping
from contextlib import AbstractContextManager
from http.client import HTTPMessage
from types import TracebackType
from typing import Any, Protocol, TypeAlias, overload, type_check_only
from typing_extensions import ParamSpec, TypeVar

from .http import HttpBaseClass, _HTTPMethod

_P = ParamSpec("_P")
_R = TypeVar("_R")

@type_check_only
class _WritableFileobj(Protocol):
    def write(self, b: bytes, /) -> object: ...
    def seek(self, offset: int, /) -> object: ...

_URI: TypeAlias = str | re.Pattern[str]
_HeaderValue: TypeAlias = str | int | bool | None
_Headers: TypeAlias = Mapping[str, _HeaderValue]
_Body: TypeAlias = str | bytes
_ResponseBody: TypeAlias = _Body | Callable[[HTTPrettyRequest, str, _Headers], tuple[int, _Headers, _Body]]

def set_default_thread_timeout(timeout: float) -> None: ...
def get_default_thread_timeout() -> float: ...

class HTTPrettyRequest(HttpBaseClass):
    headers: HTTPMessage
    raw_headers: str
    path: str
    querystring: dict[str, list[str]]
    parsed_body: Any  # It can be any object after parsing raw (str) body
    created_at: float
    def __init__(
        self, headers: str | bytes, body: _Body = "", sock: object | None = None, path_encoding: str = "iso-8859-1"
    ) -> None: ...
    @property
    def method(self) -> str: ...
    @property
    def protocol(self) -> str: ...

    @property
    def body(self) -> str: ...
    @body.setter
    def body(self, value: _Body) -> None: ...

    @property
    def url(self) -> str: ...
    @property
    def host(self) -> str: ...
    def parse_querystring(self, qs: str) -> dict[str, list[str]]: ...
    def parse_request_body(self, body: str) -> Any: ...  # Any object can be returned if deserialization is successful

class EmptyRequestHeaders(dict[str, str]): ...

class HTTPrettyRequestEmpty:
    method: str | None
    url: str | None
    body: str
    headers: EmptyRequestHeaders

class Entry(HttpBaseClass):
    method: _HTTPMethod
    uri: str
    request: HTTPrettyRequest
    body: _Body
    status: int
    streaming: bool
    adding_headers: dict[str, str]
    forcing_headers: dict[str, str]
    def __init__(
        self,
        method: str,
        uri: str,
        body: _ResponseBody,
        adding_headers: _Headers | None = None,
        forcing_headers: _Headers | None = None,
        status: int = 200,
        streaming: bool = False,
        **headers: str,
    ) -> None: ...
    def validate(self) -> None: ...
    def normalize_headers(self, headers: _Headers) -> dict[str, str]: ...
    def fill_filekind(self, fk: _WritableFileobj) -> None: ...

class URIInfo(HttpBaseClass):
    default_str_attrs: tuple[str, ...]
    username: str
    password: str
    hostname: str
    port: int
    path: str
    query: str
    scheme: str
    fragment: str
    last_request: HTTPrettyRequest | None
    def __init__(
        self,
        username: str = "",
        password: str = "",
        hostname: str = "",
        port: int = 80,
        path: str = "/",
        query: str = "",
        fragment: str = "",
        scheme: str = "",
        last_request: HTTPrettyRequest | None = None,
    ) -> None: ...
    def to_str(self, attrs: Iterable[str]) -> str: ...
    def str_with_query(self) -> str: ...
    def full_url(self, use_querystring: bool = True) -> str: ...
    def get_full_domain(self) -> str: ...
    @classmethod
    def from_uri(cls, uri: str, entry: Entry) -> URIInfo: ...

class URIMatcher:
    regex: re.Pattern[str] | None
    info: URIInfo | None
    entries: list[Entry]
    priority: int
    uri: _URI
    def __init__(self, uri: _URI, entries: Iterable[Entry], match_querystring: bool = False, priority: int = 0) -> None: ...
    def matches(self, info: URIInfo) -> bool: ...
    def get_next_entry(self, method: _HTTPMethod, info: URIInfo, request: HTTPrettyRequest) -> Entry: ...

class httpretty(HttpBaseClass):
    METHODS: tuple[_HTTPMethod, ...]
    latest_requests: list[HTTPrettyRequest]
    last_request: HTTPrettyRequest | HTTPrettyRequestEmpty
    allow_net_connect: bool
    @classmethod
    def match_uriinfo(cls, info: URIInfo) -> tuple[Entry | None, list[str]]: ...
    @classmethod
    def match_https_hostname(cls, hostname: str) -> bool: ...
    @classmethod
    def match_http_address(cls, hostname: str, port: int) -> bool: ...
    @classmethod
    def record(
        cls,
        filename: str,
        indentation: int = 4,
        encoding: str = "utf-8",
        verbose: bool = False,
        allow_net_connect: bool = True,
        # Passed to urllib3.PoolManager as kwargs, and connection pool's parameters have various types
        pool_manager_params: Mapping[str, Any] | None = None,
    ) -> AbstractContextManager[None]: ...
    @classmethod
    def playback(cls, filename: str, allow_net_connect: bool = True, verbose: bool = False) -> AbstractContextManager[None]: ...
    @classmethod
    def reset(cls) -> None: ...
    @classmethod
    def historify_request(cls, headers: str | bytes, body: _Body = "", sock: object | None = None) -> HTTPrettyRequest: ...
    @classmethod
    def register_uri(
        cls,
        method: str,
        uri: _URI,
        body: _ResponseBody = '{"message": "HTTPretty :)"}',
        adding_headers: _Headers | None = None,
        forcing_headers: _Headers | None = None,
        status: int = 200,
        responses: Iterable[Entry] | None = None,
        match_querystring: bool = False,
        priority: int = 0,
        **headers: str,
    ) -> None: ...
    @classmethod
    def Response(
        cls,
        body: _ResponseBody,
        method: _HTTPMethod | None = None,
        uri: str | None = None,
        adding_headers: _Headers | None = None,
        forcing_headers: _Headers | None = None,
        status: int = 200,
        streaming: bool = False,
        **headers: str,
    ) -> Entry: ...
    @classmethod
    def disable(cls) -> None: ...
    @classmethod
    def is_enabled(cls) -> bool: ...
    @classmethod
    def enable(cls, allow_net_connect: bool = True, verbose: bool = False) -> None: ...

class httprettized:
    allow_net_connect: bool
    verbose: bool
    def __init__(self, allow_net_connect: bool = True, verbose: bool = False) -> None: ...
    def __enter__(self) -> None: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_value: BaseException | None, traceback: TracebackType | None
    ) -> None: ...

@overload
def httprettified(test: Callable[_P, _R]) -> Callable[_P, _R]: ...
@overload
def httprettified(
    test: None = None, allow_net_connect: bool = True, verbose: bool = False
) -> Callable[[Callable[_P, _R]], Callable[_P, _R]]: ...
