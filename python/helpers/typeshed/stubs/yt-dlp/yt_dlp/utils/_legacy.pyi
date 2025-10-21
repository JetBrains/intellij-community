import ssl
import types
import urllib.request
from _socket import _Address
from _typeshed import ReadableBuffer, SupportsRead, Unused
from asyncio.events import AbstractEventLoop
from collections.abc import AsyncIterable, Awaitable, Callable, Collection, Iterable, Mapping, MutableMapping, Sequence
from http.client import HTTPResponse
from http.cookiejar import CookieJar
from socket import socket
from subprocess import Popen
from typing import Any, AnyStr, Generic, Literal, TypeVar, overload
from typing_extensions import Self

from websockets import ClientConnection, HeadersLike, LoggerLike, Origin, Subprotocol
from websockets.asyncio.client import connect
from websockets.extensions import ClientExtensionFactory

has_certifi: bool
has_websockets: bool
_T = TypeVar("_T")

class WebSocketsWrapper(Generic[_T]):
    pool: _T | None
    loop: AbstractEventLoop
    conn: connect
    def __init__(
        self,
        url: str,
        headers: Mapping[str, str] | None = None,
        connect: bool = True,
        *,
        # Passed to websockets.connect()
        origin: Origin | None = None,
        extensions: Sequence[ClientExtensionFactory] | None = None,
        subprotocols: Sequence[Subprotocol] | None = None,
        compression: str | None = "deflate",
        additional_headers: HeadersLike | None = None,
        user_agent_header: str | None = ...,
        proxy: str | Literal[True] | None = True,
        process_exception: Callable[[Exception], Exception | None] = ...,
        open_timeout: float | None = 10,
        ping_interval: float | None = 20,
        ping_timeout: float | None = 20,
        close_timeout: float | None = 10,
        max_size: int | None = 1048576,
        max_queue: int | None | tuple[int | None, int | None] = 16,
        write_limit: int | tuple[int, int | None] = 32768,
        logger: LoggerLike | None = None,
        create_connection: type[ClientConnection] | None = None,
        # Passed to AbstractEventLoop.connect() by websockets
        ssl: bool | None | ssl.SSLContext = None,
        family: int = 0,
        proto: int = 0,
        flags: int = 0,
        sock: socket | None = None,
        local_addr: None = None,
        server_hostname: str | None = None,
        ssl_handshake_timeout: float | None = None,
        ssl_shutdown_timeout: float | None = None,
        happy_eyeballs_delay: float | None = None,
        interleave: int | None = None,
    ) -> None: ...
    def __enter__(self) -> Self: ...
    def send(
        self, message: str | bytes | Iterable[str | bytes] | AsyncIterable[str | bytes], text: bool | None = None
    ) -> None: ...
    @overload
    def recv(self, decode: Literal[True]) -> str: ...
    @overload
    def recv(self, decode: Literal[False]) -> bytes: ...
    @overload
    def recv(self, decode: bool | None = None) -> str | bytes: ...
    def __exit__(
        self, type: type[BaseException] | None, value: BaseException | None, traceback: types.TracebackType | None
    ) -> None: ...
    @staticmethod
    def run_with_loop(main: Awaitable[_T], loop: AbstractEventLoop) -> _T: ...

def load_plugins(name: str, suffix: str, namespace: dict[str, Any]) -> dict[str, type[Any]]: ...
def traverse_dict(dictn: Mapping[str, Any], keys: Collection[str], casesense: bool = True) -> Any: ...
def decode_base(value: str, digits: str) -> int: ...
def platform_name() -> str: ...
def get_subprocess_encoding() -> str: ...
def register_socks_protocols() -> None: ...
def handle_youtubedl_headers(headers: dict[str, Any]) -> dict[str, Any]: ...
def request_to_url(req: urllib.request.Request | str) -> str: ...
def sanitized_Request(
    url: str,
    data: ReadableBuffer | SupportsRead[bytes] | Iterable[bytes] | None = None,
    headers: MutableMapping[str, str] = {},
    origin_req_host: str | None = None,
    unverifiable: bool = False,
    method: str | None = None,
) -> urllib.request.Request: ...

class YoutubeDLHandler(urllib.request.AbstractHTTPHandler):
    def __init__(
        self,
        params: Mapping[str, Any],
        *args: Any,  # args passed to urllib.request.AbstractHTTPHandler.__init__().
        context: Any = None,
        source_address: _Address | None = None,
        debuglevel: int | None = None,
    ) -> None: ...

YoutubeDLHTTPSHandler = YoutubeDLHandler

class YoutubeDLCookieProcessor(urllib.request.HTTPCookieProcessor):
    def __init__(self, cookiejar: CookieJar | None = None) -> None: ...
    def http_response(self, request: urllib.request.Request, response: HTTPResponse) -> HTTPResponse: ...
    https_request: Callable[[urllib.request.HTTPCookieProcessor, urllib.request.Request], HTTPResponse]  # type: ignore[assignment]
    https_response = http_response

def make_HTTPS_handler(
    params: Mapping[str, Any], *, debuglevel: int | None = None, source_address: _Address | None = None
) -> YoutubeDLHTTPSHandler: ...
def process_communicate_or_kill(
    p: Popen[Any], *args: Any, **kwargs: Any  # args/kwargs passed to Popen.__init__().
) -> tuple[AnyStr, AnyStr]: ...
def encodeFilename(s: str, for_subprocess: Unused = False) -> bytes: ...
def decodeFilename(b: bytes, for_subprocess: Unused = False) -> str: ...
def decodeArgument(b: _T) -> _T: ...
def decodeOption(optval: AnyStr) -> str: ...
def error_to_compat_str(err: Any) -> str: ...  # Calls str(err).
