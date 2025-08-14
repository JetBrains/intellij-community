from collections.abc import AsyncIterable, AsyncIterator, Awaitable, Callable, Iterable, Iterator, Mapping
from http import HTTPStatus
from http.cookies import SimpleCookie
from io import BytesIO, IOBase
from json import JSONEncoder
from re import Pattern
from types import TracebackType
from typing import Any, Generic, Literal, NoReturn, TypedDict, TypeVar, type_check_only

from asgiref.typing import ASGIVersions
from django.contrib.auth.base_user import _UserModel
from django.contrib.sessions.backends.base import SessionBase
from django.core.handlers.asgi import ASGIRequest
from django.core.handlers.base import BaseHandler
from django.core.handlers.wsgi import WSGIRequest
from django.http.request import HttpRequest
from django.http.response import HttpResponseBase
from django.template.base import Template
from django.test.utils import ContextList
from django.urls import ResolverMatch
from typing_extensions import TypeAlias

BOUNDARY: str
MULTIPART_CONTENT: str
CONTENT_TYPE_RE: Pattern[str]
JSON_CONTENT_TYPE_RE: Pattern[str]
REDIRECT_STATUS_CODES: frozenset[HTTPStatus]

class RedirectCycleError(Exception):
    last_response: HttpResponseBase
    redirect_chain: list[tuple[str, int]]
    def __init__(self, message: str, last_response: HttpResponseBase) -> None: ...

class FakePayload(IOBase):
    read_started: bool
    def __init__(self, initial_bytes: bytes | str | None = ...) -> None: ...
    def __len__(self) -> int: ...
    def read(self, size: int = ...) -> bytes: ...
    def readline(self, size: int | None = ..., /) -> bytes: ...
    def write(self, content: bytes | str) -> None: ...

_T = TypeVar("_T")

def closing_iterator_wrapper(iterable: Iterable[_T], close: Callable[[], Any]) -> Iterator[_T]: ...
async def aclosing_iterator_wrapper(iterable: AsyncIterable[_T], close: Callable[[], Any]) -> AsyncIterator[_T]: ...
def conditional_content_removal(request: HttpRequest, response: HttpResponseBase) -> HttpResponseBase: ...
@type_check_only
class _WSGIResponse(HttpResponseBase):
    wsgi_request: WSGIRequest

@type_check_only
class _ASGIResponse(HttpResponseBase):
    asgi_request: ASGIRequest

class ClientHandler(BaseHandler):
    enforce_csrf_checks: bool
    def __init__(self, enforce_csrf_checks: bool = ..., *args: Any, **kwargs: Any) -> None: ...
    def __call__(self, environ: dict[str, Any]) -> _WSGIResponse: ...

class AsyncClientHandler(BaseHandler):
    enforce_csrf_checks: bool
    def __init__(self, enforce_csrf_checks: bool = ..., *args: Any, **kwargs: Any) -> None: ...
    async def __call__(self, scope: dict[str, Any]) -> _ASGIResponse: ...

def encode_multipart(boundary: str, data: dict[str, Any]) -> bytes: ...
def encode_file(boundary: str, key: str, file: Any) -> list[bytes]: ...

_GetDataType: TypeAlias = (
    Mapping[str, str | bytes | int | Iterable[str | bytes | int]]
    | Iterable[tuple[str, str | bytes | int | Iterable[str | bytes | int]]]
    | None
)

@type_check_only
class _RequestFactory(Generic[_T]):
    json_encoder: type[JSONEncoder]
    defaults: dict[str, str]
    cookies: SimpleCookie
    errors: BytesIO
    def __init__(
        self, *, json_encoder: type[JSONEncoder] = ..., headers: Mapping[str, Any] | None = ..., **defaults: Any
    ) -> None: ...
    def request(self, **request: Any) -> _T: ...
    def get(
        self,
        path: str,
        data: _GetDataType = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _T: ...
    def post(
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _T: ...
    def head(
        self, path: str, data: Any = ..., secure: bool = ..., *, headers: Mapping[str, Any] | None = ..., **extra: Any
    ) -> _T: ...
    def trace(self, path: str, secure: bool = ..., *, headers: Mapping[str, Any] | None = ..., **extra: Any) -> _T: ...
    def options(
        self,
        path: str,
        data: dict[str, str] | str = ...,
        content_type: str = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _T: ...
    def put(
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _T: ...
    def patch(
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _T: ...
    def delete(
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _T: ...
    def generic(
        self,
        method: str,
        path: str,
        data: Any = ...,
        content_type: str | None = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _T: ...

class RequestFactory(_RequestFactory[WSGIRequest]): ...

# A non total duplication of `asgiref.typing.HTTPScope`
@type_check_only
class _HTTPScope(TypedDict, total=False):
    type: Literal["http"]
    asgi: ASGIVersions
    http_version: str
    method: str
    scheme: str
    path: str
    raw_path: bytes
    query_string: bytes
    root_path: str
    headers: Iterable[tuple[bytes, bytes]]
    client: tuple[str, int] | None
    server: tuple[str, int | None] | None
    state: dict[str, Any]
    extensions: dict[str, dict[object, object]] | None

@type_check_only
class _AsyncRequestFactory(_RequestFactory[_T]):
    defaults: _HTTPScope  # type: ignore[assignment]

class AsyncRequestFactory(_AsyncRequestFactory[ASGIRequest]): ...

# fakes to distinguish WSGIRequest and ASGIRequest
@type_check_only
class _MonkeyPatchedWSGIResponse(_WSGIResponse):
    def json(self) -> Any: ...
    request: dict[str, Any]
    client: Client
    templates: list[Template]
    context: ContextList | dict[str, Any]
    content: bytes
    resolver_match: ResolverMatch
    redirect_chain: list[tuple[str, int]]

@type_check_only
class _MonkeyPatchedASGIResponse(_ASGIResponse):
    def json(self) -> Any: ...
    request: dict[str, Any]
    client: AsyncClient
    templates: list[Template]
    context: ContextList | dict[str, Any]
    content: bytes
    resolver_match: ResolverMatch
    redirect_chain: list[tuple[str, int]]

class ClientMixin:
    def store_exc_info(self, **kwargs: Any) -> None: ...
    def check_exception(self, response: HttpResponseBase) -> NoReturn: ...
    @property
    def session(self) -> SessionBase: ...
    async def asession(self) -> SessionBase: ...
    def login(self, **credentials: Any) -> bool: ...
    async def alogin(self, **credentials: Any) -> bool: ...
    def force_login(self, user: _UserModel, backend: str | None = ...) -> None: ...
    async def aforce_login(self, user: _UserModel, backend: str | None = ...) -> None: ...
    def logout(self) -> None: ...
    async def alogout(self) -> None: ...

class Client(ClientMixin, _RequestFactory[_MonkeyPatchedWSGIResponse]):
    handler: ClientHandler
    raise_request_exception: bool
    exc_info: tuple[type[BaseException], BaseException, TracebackType] | None
    extra: dict[str, Any] | None
    headers: dict[str, Any]
    def __init__(
        self,
        enforce_csrf_checks: bool = ...,
        raise_request_exception: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **defaults: Any,
    ) -> None: ...
    def request(self, **request: Any) -> _MonkeyPatchedWSGIResponse: ...
    def get(  # type: ignore[override]
        self,
        path: str,
        data: _GetDataType = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedWSGIResponse: ...
    def post(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedWSGIResponse: ...
    def head(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedWSGIResponse: ...
    def options(  # type: ignore[override]
        self,
        path: str,
        data: dict[str, str] | str = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedWSGIResponse: ...
    def put(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedWSGIResponse: ...
    def patch(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedWSGIResponse: ...
    def delete(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedWSGIResponse: ...
    def trace(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedWSGIResponse: ...

class AsyncClient(ClientMixin, _AsyncRequestFactory[Awaitable[_MonkeyPatchedASGIResponse]]):
    handler: AsyncClientHandler
    raise_request_exception: bool
    exc_info: Any
    extra: dict[str, Any] | None
    headers: dict[str, Any]
    def __init__(
        self,
        enforce_csrf_checks: bool = ...,
        raise_request_exception: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **defaults: Any,
    ) -> None: ...
    async def request(self, **request: Any) -> _MonkeyPatchedASGIResponse: ...
    async def get(  # type: ignore[override]
        self,
        path: str,
        data: _GetDataType = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedASGIResponse: ...
    async def post(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedASGIResponse: ...
    async def head(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedASGIResponse: ...
    async def options(  # type: ignore[override]
        self,
        path: str,
        data: dict[str, str] | str = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedASGIResponse: ...
    async def put(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedASGIResponse: ...
    async def patch(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedASGIResponse: ...
    async def delete(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        content_type: str = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedASGIResponse: ...
    async def trace(  # type: ignore[override]
        self,
        path: str,
        data: Any = ...,
        follow: bool = ...,
        secure: bool = ...,
        *,
        headers: Mapping[str, Any] | None = ...,
        **extra: Any,
    ) -> _MonkeyPatchedASGIResponse: ...
