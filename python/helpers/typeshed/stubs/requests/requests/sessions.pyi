from _typeshed import Self, SupportsItems, SupportsRead
from collections.abc import Callable, Iterable, Mapping, MutableMapping
from typing import IO, Any, Union
from typing_extensions import TypeAlias

from urllib3._collections import RecentlyUsedContainer

from . import adapters, auth as _auth, compat, cookies, exceptions, hooks, models, status_codes, utils
from .models import Response
from .structures import CaseInsensitiveDict as CaseInsensitiveDict

_BaseAdapter: TypeAlias = adapters.BaseAdapter
OrderedDict = compat.OrderedDict
cookiejar_from_dict = cookies.cookiejar_from_dict
extract_cookies_to_jar = cookies.extract_cookies_to_jar
RequestsCookieJar = cookies.RequestsCookieJar
merge_cookies = cookies.merge_cookies
Request = models.Request
PreparedRequest = models.PreparedRequest
DEFAULT_REDIRECT_LIMIT = models.DEFAULT_REDIRECT_LIMIT
default_hooks = hooks.default_hooks
dispatch_hook = hooks.dispatch_hook
to_key_val_list = utils.to_key_val_list
default_headers = utils.default_headers
to_native_string = utils.to_native_string
TooManyRedirects = exceptions.TooManyRedirects
InvalidSchema = exceptions.InvalidSchema
ChunkedEncodingError = exceptions.ChunkedEncodingError
ContentDecodingError = exceptions.ContentDecodingError
HTTPAdapter = adapters.HTTPAdapter
requote_uri = utils.requote_uri
get_environ_proxies = utils.get_environ_proxies
get_netrc_auth = utils.get_netrc_auth
should_bypass_proxies = utils.should_bypass_proxies
get_auth_from_url = utils.get_auth_from_url
codes = status_codes.codes
REDIRECT_STATI = models.REDIRECT_STATI

def merge_setting(request_setting, session_setting, dict_class=...): ...
def merge_hooks(request_hooks, session_hooks, dict_class=...): ...

class SessionRedirectMixin:
    def resolve_redirects(self, resp, req, stream=..., timeout=..., verify=..., cert=..., proxies=...): ...
    def rebuild_auth(self, prepared_request, response): ...
    def rebuild_proxies(self, prepared_request, proxies): ...
    def should_strip_auth(self, old_url, new_url): ...

_Data: TypeAlias = str | bytes | Mapping[str, Any] | Iterable[tuple[str, str | None]] | IO[Any]
_Auth: TypeAlias = Union[tuple[str, str], _auth.AuthBase, Callable[[PreparedRequest], PreparedRequest]]
_Cert: TypeAlias = Union[str, tuple[str, str]]
_Files: TypeAlias = (
    Mapping[str, SupportsRead[str | bytes] | str | bytes]
    | Mapping[str, tuple[str | None, SupportsRead[str | bytes] | str | bytes]]
    | Mapping[str, tuple[str | None, SupportsRead[str | bytes] | str | bytes, str]]
    | Mapping[str, tuple[str | None, SupportsRead[str | bytes] | str | bytes, str, _TextMapping]]
)
_Hook: TypeAlias = Callable[[Response], Any]
_Hooks: TypeAlias = Mapping[str, _Hook | list[_Hook]]
_HooksInput: TypeAlias = Mapping[str, Iterable[_Hook] | _Hook]

_ParamsMappingKeyType: TypeAlias = str | bytes | int | float
_ParamsMappingValueType: TypeAlias = str | bytes | int | float | Iterable[str | bytes | int | float] | None
_Params: TypeAlias = Union[
    SupportsItems[_ParamsMappingKeyType, _ParamsMappingValueType],
    tuple[_ParamsMappingKeyType, _ParamsMappingValueType],
    Iterable[tuple[_ParamsMappingKeyType, _ParamsMappingValueType]],
    str | bytes,
]
_TextMapping: TypeAlias = MutableMapping[str, str]
_Timeout: TypeAlias = Union[float, tuple[float, float], tuple[float, None]]
_Verify: TypeAlias = bool | str

class Session(SessionRedirectMixin):
    __attrs__: Any
    headers: CaseInsensitiveDict[str]
    auth: _Auth | None
    proxies: _TextMapping
    hooks: _Hooks
    params: _Params
    stream: bool
    verify: None | bool | str
    cert: None | str | tuple[str, str]
    max_redirects: int
    trust_env: bool
    cookies: RequestsCookieJar
    adapters: MutableMapping[Any, Any]
    redirect_cache: RecentlyUsedContainer[Any, Any]
    def __init__(self) -> None: ...
    def __enter__(self: Self) -> Self: ...
    def __exit__(self, *args) -> None: ...
    def prepare_request(self, request: Request) -> PreparedRequest: ...
    def request(
        self,
        method: str | bytes,
        url: str | bytes,
        params: _Params | None = ...,
        data: _Data | None = ...,
        headers: _TextMapping | None = ...,
        cookies: None | RequestsCookieJar | _TextMapping = ...,
        files: _Files | None = ...,
        auth: _Auth | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        proxies: _TextMapping | None = ...,
        hooks: _HooksInput | None = ...,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        cert: _Cert | None = ...,
        json: Any | None = ...,
    ) -> Response: ...
    def get(
        self,
        url: str | bytes,
        *,
        params: _Params | None = ...,
        data: _Data | None = ...,
        headers: _TextMapping | None = ...,
        cookies: RequestsCookieJar | _TextMapping | None = ...,
        files: _Files | None = ...,
        auth: _Auth | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        proxies: _TextMapping | None = ...,
        hooks: _HooksInput | None = ...,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        cert: _Cert | None = ...,
        json: Any | None = ...,
    ) -> Response: ...
    def options(
        self,
        url: str | bytes,
        *,
        params: _Params | None = ...,
        data: _Data | None = ...,
        headers: _TextMapping | None = ...,
        cookies: RequestsCookieJar | _TextMapping | None = ...,
        files: _Files | None = ...,
        auth: _Auth | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        proxies: _TextMapping | None = ...,
        hooks: _HooksInput | None = ...,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        cert: _Cert | None = ...,
        json: Any | None = ...,
    ) -> Response: ...
    def head(
        self,
        url: str | bytes,
        *,
        params: _Params | None = ...,
        data: _Data | None = ...,
        headers: _TextMapping | None = ...,
        cookies: RequestsCookieJar | _TextMapping | None = ...,
        files: _Files | None = ...,
        auth: _Auth | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        proxies: _TextMapping | None = ...,
        hooks: _HooksInput | None = ...,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        cert: _Cert | None = ...,
        json: Any | None = ...,
    ) -> Response: ...
    def post(
        self,
        url: str | bytes,
        data: _Data | None = ...,
        json: Any | None = ...,
        *,
        params: _Params | None = ...,
        headers: _TextMapping | None = ...,
        cookies: RequestsCookieJar | _TextMapping | None = ...,
        files: _Files | None = ...,
        auth: _Auth | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        proxies: _TextMapping | None = ...,
        hooks: _HooksInput | None = ...,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        cert: _Cert | None = ...,
    ) -> Response: ...
    def put(
        self,
        url: str | bytes,
        data: _Data | None = ...,
        *,
        params: _Params | None = ...,
        headers: _TextMapping | None = ...,
        cookies: RequestsCookieJar | _TextMapping | None = ...,
        files: _Files | None = ...,
        auth: _Auth | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        proxies: _TextMapping | None = ...,
        hooks: _HooksInput | None = ...,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        cert: _Cert | None = ...,
        json: Any | None = ...,
    ) -> Response: ...
    def patch(
        self,
        url: str | bytes,
        data: _Data | None = ...,
        *,
        params: _Params | None = ...,
        headers: _TextMapping | None = ...,
        cookies: RequestsCookieJar | _TextMapping | None = ...,
        files: _Files | None = ...,
        auth: _Auth | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        proxies: _TextMapping | None = ...,
        hooks: _HooksInput | None = ...,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        cert: _Cert | None = ...,
        json: Any | None = ...,
    ) -> Response: ...
    def delete(
        self,
        url: str | bytes,
        *,
        params: _Params | None = ...,
        data: _Data | None = ...,
        headers: _TextMapping | None = ...,
        cookies: RequestsCookieJar | _TextMapping | None = ...,
        files: _Files | None = ...,
        auth: _Auth | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        proxies: _TextMapping | None = ...,
        hooks: _HooksInput | None = ...,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        cert: _Cert | None = ...,
        json: Any | None = ...,
    ) -> Response: ...
    def send(
        self,
        request: PreparedRequest,
        *,
        stream: bool | None = ...,
        verify: _Verify | None = ...,
        proxies: _TextMapping | None = ...,
        cert: _Cert | None = ...,
        timeout: _Timeout | None = ...,
        allow_redirects: bool = ...,
        **kwargs: Any,
    ) -> Response: ...
    def merge_environment_settings(self, url, proxies, stream, verify, cert): ...
    def get_adapter(self, url: str) -> _BaseAdapter: ...
    def close(self) -> None: ...
    def mount(self, prefix: str | bytes, adapter: _BaseAdapter) -> None: ...

def session() -> Session: ...
