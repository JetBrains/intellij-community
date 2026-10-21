from typing import Final

from .core import (
    EmptyRequestHeaders as EmptyRequestHeaders,
    Entry as Entry,
    HTTPrettyRequest as HTTPrettyRequest,
    HTTPrettyRequestEmpty as HTTPrettyRequestEmpty,
    URIInfo as URIInfo,
    URIMatcher as URIMatcher,
    get_default_thread_timeout as get_default_thread_timeout,
    httprettified as httprettified,
    httprettized as httprettized,
    httpretty as httpretty,
    set_default_thread_timeout as set_default_thread_timeout,
)
from .errors import HTTPrettyError as HTTPrettyError, UnmockedError as UnmockedError

__version__: Final[str]
HTTPretty = httpretty
activate = httprettified
enabled = httprettized
enable = httpretty.enable
register_uri = httpretty.register_uri
disable = httpretty.disable
is_enabled = httpretty.is_enabled
reset = httpretty.reset
Response = httpretty.Response
GET: Final = "GET"
PUT: Final = "PUT"
POST: Final = "POST"
DELETE: Final = "DELETE"
HEAD: Final = "HEAD"
PATCH: Final = "PATCH"
OPTIONS: Final = "OPTIONS"
CONNECT: Final = "CONNECT"

def last_request() -> HTTPrettyRequest | HTTPrettyRequestEmpty: ...
def latest_requests() -> list[HTTPrettyRequest]: ...
def has_request() -> bool: ...
