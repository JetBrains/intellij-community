from datetime import timedelta
from logging import Logger
from typing import Any, Iterable, Pattern, TypeVar, overload
from typing_extensions import TypedDict

_IterableT = TypeVar("_IterableT", bound=Iterable[Any])
_T = TypeVar("_T")
_App = Any  # flask is not part of typeshed
_Response = Any  # flask is not part of typeshed
_MultiDict = Any  # werkzeug is not part of typeshed

class _Options(TypedDict, total=False):
    resources: dict[str, dict[str, Any]] | list[str] | str | None
    origins: str | list[str] | None
    methods: str | list[str] | None
    expose_headers: str | list[str] | None
    allow_headers: str | list[str] | None
    supports_credentials: bool | None
    max_age: timedelta | int | str | None
    send_wildcard: bool | None
    vary_header: bool | None
    automatic_options: bool | None
    intercept_exceptions: bool | None
    always_send: bool | None

LOG: Logger
ACL_ORIGIN: str
ACL_METHODS: str
ACL_ALLOW_HEADERS: str
ACL_EXPOSE_HEADERS: str
ACL_CREDENTIALS: str
ACL_MAX_AGE: str
ACL_REQUEST_METHOD: str
ACL_REQUEST_HEADERS: str
ALL_METHODS: list[str]
CONFIG_OPTIONS: list[str]
FLASK_CORS_EVALUATED: str
RegexObject = Pattern[str]
DEFAULT_OPTIONS: _Options

def parse_resources(resources: dict[str, _Options] | Iterable[str] | str | Pattern[str]) -> list[tuple[str, _Options]]: ...
def get_regexp_pattern(regexp: str | Pattern[str]) -> str: ...
def get_cors_origins(options: _Options, request_origin: str | None) -> list[str] | None: ...
def get_allow_headers(options: _Options, acl_request_headers: str | None) -> str | None: ...
def get_cors_headers(options: _Options, request_headers: dict[str, Any], request_method: str) -> _MultiDict: ...
def set_cors_headers(resp: _Response, options: _Options) -> _Response: ...
def probably_regex(maybe_regex: str | Pattern[str]) -> bool: ...
def re_fix(reg: str) -> str: ...
def try_match_any(inst: str, patterns: Iterable[str | Pattern[str]]) -> bool: ...
def try_match(request_origin: str, maybe_regex: str | Pattern[str]) -> bool: ...
def get_cors_options(appInstance: _App | None, *dicts: _Options) -> _Options: ...
def get_app_kwarg_dict(appInstance: _App | None = ...) -> _Options: ...
def flexible_str(obj: object) -> str | None: ...
def serialize_option(options_dict: _Options, key: str, upper: bool = ...) -> None: ...
@overload
def ensure_iterable(inst: str) -> list[str]: ...  # type: ignore
@overload
def ensure_iterable(inst: _IterableT) -> _IterableT: ...  # type: ignore
@overload
def ensure_iterable(inst: _T) -> list[_T]: ...
def sanitize_regex_param(param: str | list[str]) -> list[str]: ...
def serialize_options(opts: _Options) -> _Options: ...
