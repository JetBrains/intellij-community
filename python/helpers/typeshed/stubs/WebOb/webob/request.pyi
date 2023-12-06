import datetime
import io
from _typeshed import (
    ExcInfo,
    Incomplete,
    ReadableBuffer,
    SupportsItems,
    SupportsKeysAndGetItem,
    SupportsNoArgReadline,
    SupportsRead,
)
from _typeshed.wsgi import WSGIApplication, WSGIEnvironment
from cgi import FieldStorage
from collections.abc import Iterable, Mapping
from re import Pattern
from tempfile import _TemporaryFileWrapper
from typing import IO, Any, ClassVar, Protocol, TypeVar, overload
from typing_extensions import Literal, Self, TypeAlias, TypedDict

from webob.acceptparse import _AcceptCharsetProperty, _AcceptEncodingProperty, _AcceptLanguageProperty, _AcceptProperty
from webob.byterange import Range
from webob.cachecontrol import _RequestCacheControl
from webob.cookies import RequestCookies
from webob.descriptors import _AsymmetricProperty, _AsymmetricPropertyWithDelete, _authorization, _DateProperty
from webob.etag import IfRange, IfRangeDate, _ETagProperty
from webob.headers import EnvironHeaders
from webob.multidict import GetDict, MultiDict, NestedMultiDict, NoVars
from webob.response import Response, _HTTPHeader

_T = TypeVar("_T")
_HTTPMethod: TypeAlias = Literal["GET", "HEAD", "POST", "PUT", "DELETE", "PATCH"]
_ListOrTuple: TypeAlias = list[_T] | tuple[_T, ...]

class _SupportsReadAndNoArgReadline(SupportsRead[bytes], SupportsNoArgReadline[bytes], Protocol): ...

class _RequestCacheControlDict(TypedDict, total=False):
    max_stale: int
    min_stale: int
    only_if_cached: bool
    no_cache: Literal[True] | str
    no_store: bool
    no_transform: bool
    max_age: int

class _FieldStorageWithFile(FieldStorage):
    file: IO[bytes]
    filename: str

class _NoDefault: ...

NoDefault: _NoDefault

class BaseRequest:
    request_body_tempfile_limit: ClassVar[int]
    environ: WSGIEnvironment
    method: _HTTPMethod
    def __init__(self, environ: WSGIEnvironment, **kw: Any) -> None: ...
    def encget(self, key: str, default: Any = ..., encattr: str | None = None) -> Any: ...
    def encset(self, key: str, val: Any, encattr: str | None = None) -> None: ...
    @property
    def charset(self) -> str | None: ...
    def decode(self, charset: str | None = None, errors: str = "strict") -> Self: ...
    @property
    def body_file(self) -> SupportsRead[bytes]: ...
    @body_file.setter
    def body_file(self, value: SupportsRead[bytes]) -> None: ...
    content_length: int | None
    body_file_raw: SupportsRead[bytes]
    is_body_seekable: bool
    @property
    def body_file_seekable(self) -> IO[bytes]: ...
    url_encoding: str
    @property
    def scheme(self) -> str | None: ...
    @scheme.setter
    def scheme(self, value: str | None) -> None: ...
    @property
    def http_version(self) -> str | None: ...
    @http_version.setter
    def http_version(self, value: str | None) -> None: ...
    remote_user: str | None
    remote_host: str | None
    remote_addr: str | None
    query_string: str
    @property
    def server_name(self) -> str | None: ...
    @server_name.setter
    def server_name(self, value: str | None) -> None: ...
    @property
    def server_port(self) -> int | None: ...
    @server_port.setter
    def server_port(self, value: int | None) -> None: ...
    script_name: str
    @property
    def path_info(self) -> str | None: ...
    @path_info.setter
    def path_info(self, value: str | None) -> None: ...
    uscript_name: str  # bw compat
    upath_info = path_info  # bw compat
    content_type: str | None
    headers: _AsymmetricProperty[EnvironHeaders, SupportsItems[str, str] | Iterable[tuple[str, str]]]
    @property
    def client_addr(self) -> str | None: ...
    @property
    def host_port(self) -> str: ...
    @property
    def host_url(self) -> str: ...
    @property
    def application_url(self) -> str: ...
    @property
    def path_url(self) -> str: ...
    @property
    def path(self) -> str: ...
    @property
    def path_qs(self) -> str: ...
    @property
    def url(self) -> str: ...
    def relative_url(self, other_url: str, to_application: bool = False) -> str: ...
    def path_info_pop(self, pattern: Pattern[str] | None = None) -> str | None: ...
    def path_info_peek(self) -> str | None: ...
    urlvars: dict[str, str]
    urlargs: tuple[str]
    @property
    def is_xhr(self) -> bool: ...
    host: str
    @property
    def domain(self) -> str: ...
    body: bytes
    json: Any
    json_body: Any
    text: str
    @property
    def POST(self) -> MultiDict[str, str | _FieldStorageWithFile] | NoVars: ...
    @property
    def GET(self) -> GetDict: ...
    @property
    def params(self) -> NestedMultiDict[str, str | _FieldStorageWithFile]: ...
    cookies: _AsymmetricProperty[RequestCookies, SupportsKeysAndGetItem[str, str] | Iterable[tuple[str, str]]]
    def copy(self) -> Self: ...
    def copy_get(self) -> Self: ...
    @property
    def is_body_readable(self) -> bool: ...
    @is_body_readable.setter
    def is_body_readable(self, flag: bool) -> None: ...
    def make_body_seekable(self) -> None: ...
    def copy_body(self) -> None: ...
    def make_tempfile(self) -> _TemporaryFileWrapper[bytes]: ...
    def remove_conditional_headers(
        self, remove_encoding: bool = True, remove_range: bool = True, remove_match: bool = True, remove_modified: bool = True
    ) -> None: ...
    accept: _AcceptProperty
    accept_charset: _AcceptCharsetProperty
    accept_encoding: _AcceptEncodingProperty
    accept_language: _AcceptLanguageProperty
    authorization: _AsymmetricPropertyWithDelete[_authorization | None, tuple[str, str | dict[str, str]] | list[Any] | str | None]
    cache_control: _AsymmetricPropertyWithDelete[
        _RequestCacheControl | None, _RequestCacheControl | _RequestCacheControlDict | str | None
    ]
    if_match: _ETagProperty
    if_none_match: _ETagProperty
    date: _DateProperty
    if_modified_since: _DateProperty
    if_unmodified_since: _DateProperty
    if_range: _AsymmetricPropertyWithDelete[
        IfRange | IfRangeDate | None, IfRange | IfRangeDate | datetime.datetime | datetime.date | str | None
    ]
    max_forwards: int | None
    pragma: str | None
    range: _AsymmetricPropertyWithDelete[Range, tuple[int, int | None] | list[int | None] | str | None]
    referer: str | None
    referrer: str | None
    user_agent: str | None
    def as_bytes(self, skip_body: bool = False) -> bytes: ...
    def as_text(self) -> str: ...
    @classmethod
    def from_bytes(cls, b: bytes) -> Self: ...
    @classmethod
    def from_text(cls, s: str) -> Self: ...
    @classmethod
    def from_file(cls, fp: _SupportsReadAndNoArgReadline) -> Self: ...
    @overload
    def call_application(
        self, application: WSGIApplication, catch_exc_info: Literal[False] = False
    ) -> tuple[str, list[_HTTPHeader], Iterable[bytes]]: ...
    @overload
    def call_application(
        self, application: WSGIApplication, catch_exc_info: Literal[True]
    ) -> tuple[str, list[_HTTPHeader], Iterable[bytes], ExcInfo | None]: ...
    ResponseClass: type[Response]
    def send(self, application: WSGIApplication | None = None, catch_exc_info: bool = False) -> Response: ...
    get_response = send
    def make_default_send_app(self) -> WSGIApplication: ...
    @classmethod
    def blank(
        cls,
        path: str,
        environ: dict[str, None] | None = None,
        base_url: str | None = None,
        headers: Mapping[str, str] | None = None,
        POST: str | bytes | Mapping[Any, Any] | Mapping[Any, _ListOrTuple[Any]] | None = None,
        **kw,
    ) -> Self: ...

class LegacyRequest(BaseRequest):
    uscript_name: Incomplete
    upath_info: Incomplete
    def encget(self, key, default=..., encattr: Incomplete | None = None): ...

class AdhocAttrMixin:
    def __setattr__(self, attr: str, value: Any) -> None: ...
    def __getattr__(self, attr: str) -> Any: ...
    def __delattr__(self, attr: str) -> None: ...

class Request(AdhocAttrMixin, BaseRequest): ...
class DisconnectionError(IOError): ...

def environ_from_url(path: str) -> WSGIEnvironment: ...
def environ_add_POST(
    env: WSGIEnvironment,
    data: str | bytes | Mapping[Any, Any] | Mapping[Any, _ListOrTuple[Any]] | None,
    content_type: str | None = None,
) -> None: ...

class LimitedLengthFile(io.RawIOBase):
    file: SupportsRead[bytes]
    maxlen: int
    remaining: int
    def __init__(self, file: SupportsRead[bytes], maxlen: int) -> None: ...
    @staticmethod
    def readable() -> Literal[True]: ...
    def readinto(self, buff: ReadableBuffer) -> int: ...

class Transcoder:
    charset: str
    errors: str
    def __init__(self, charset: str, errors: str = "strict") -> None: ...
    def transcode_query(self, q: str) -> str: ...
    def transcode_fs(self, fs: FieldStorage, content_type: str) -> io.BytesIO: ...
