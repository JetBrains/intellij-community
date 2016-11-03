# Stubs for http.client (Python 3.4)

from typing import (
    Any, Dict, IO, Iterable, List, Iterator, Mapping, Optional, Tuple, TypeVar,
    Union,
    overload,
)
import email.message
import io
import sys
import ssl
import types

_DataType = Union[bytes, IO[Any], Iterable[bytes], str]
_T = TypeVar('_T')

HTTP_PORT = ...  # type: int
HTTPS_PORT = ...  # type: int

CONTINUE = ...  # type: int
SWITCHING_PROTOCOLS = ...  # type: int
PROCESSING = ...  # type: int

OK = ...  # type: int
CREATED = ...  # type: int
ACCEPTED = ...  # type: int
NON_AUTHORITATIVE_INFORMATION = ...  # type: int
NO_CONTENT = ...  # type: int
RESET_CONTENT = ...  # type: int
PARTIAL_CONTENT = ...  # type: int
MULTI_STATUS = ...  # type: int
IM_USED = ...  # type: int

MULTIPLE_CHOICES = ...  # type: int
MOVED_PERMANENTLY = ...  # type: int
FOUND = ...  # type: int
SEE_OTHER = ...  # type: int
NOT_MODIFIED = ...  # type: int
USE_PROXY = ...  # type: int
TEMPORARY_REDIRECT = ...  # type: int

BAD_REQUEST = ...  # type: int
UNAUTHORIZED = ...  # type: int
PAYMENT_REQUIRED = ...  # type: int
FORBIDDEN = ...  # type: int
NOT_FOUND = ...  # type: int
METHOD_NOT_ALLOWED = ...  # type: int
NOT_ACCEPTABLE = ...  # type: int
PROXY_AUTHENTICATION_REQUIRED = ...  # type: int
REQUEST_TIMEOUT = ...  # type: int
CONFLICT = ...  # type: int
GONE = ...  # type: int
LENGTH_REQUIRED = ...  # type: int
PRECONDITION_FAILED = ...  # type: int
REQUEST_ENTITY_TOO_LARGE = ...  # type: int
REQUEST_URI_TOO_LONG = ...  # type: int
UNSUPPORTED_MEDIA_TYPE = ...  # type: int
REQUESTED_RANGE_NOT_SATISFIABLE = ...  # type: int
EXPECTATION_FAILED = ...  # type: int
UNPROCESSABLE_ENTITY = ...  # type: int
LOCKED = ...  # type: int
FAILED_DEPENDENCY = ...  # type: int
UPGRADE_REQUIRED = ...  # type: int
PRECONDITION_REQUIRED = ...  # type: int
TOO_MANY_REQUESTS = ...  # type: int
REQUEST_HEADER_FIELDS_TOO_LARGE = ...  # type: int

INTERNAL_SERVER_ERROR = ...  # type: int
NOT_IMPLEMENTED = ...  # type: int
BAD_GATEWAY = ...  # type: int
SERVICE_UNAVAILABLE = ...  # type: int
GATEWAY_TIMEOUT = ...  # type: int
HTTP_VERSION_NOT_SUPPORTED = ...  # type: int
INSUFFICIENT_STORAGE = ...  # type: int
NOT_EXTENDED = ...  # type: int
NETWORK_AUTHENTICATION_REQUIRED = ...  # type: int

responses = ...  # type: Dict[int, str]

class HTTPMessage(email.message.Message): ...

if sys.version_info >= (3, 5):
    class HTTPResponse(io.BufferedIOBase):
        msg = ...  # type: HTTPMessage
        version = ...  # type: int
        debuglevel = ...  # type: int
        closed = ...  # type: bool
        status = ...  # type: int
        reason = ...  # type: str
        def read(self, amt: Optional[int] = ...) -> bytes: ...
        def readinto(self, b: bytearray) -> int: ...
        @overload
        def getheader(self, name: str) -> Optional[str]: ...
        @overload
        def getheader(self, name: str, default: _T) -> Union[str, _T]: ...
        def getheaders(self) -> List[Tuple[str, str]]: ...
        def fileno(self) -> int: ...
        def __iter__(self) -> Iterator[bytes]: ...
        def __enter__(self) -> 'HTTPResponse': ...
        def __exit__(self, exc_type: Optional[type],
                     exc_val: Optional[Exception],
                     exc_tb: Optional[types.TracebackType]) -> bool: ...
else:
    class HTTPResponse:
        msg = ...  # type: HTTPMessage
        version = ...  # type: int
        debuglevel = ...  # type: int
        closed = ...  # type: bool
        status = ...  # type: int
        reason = ...  # type: str
        def read(self, amt: Optional[int] = ...) -> bytes: ...
        if sys.version_info >= (3, 3):
            def readinto(self, b: bytearray) -> int: ...
        @overload
        def getheader(self, name: str) -> Optional[str]: ...
        @overload
        def getheader(self, name: str, default: _T) -> Union[str, _T]: ...
        def getheaders(self) -> List[Tuple[str, str]]: ...
        def fileno(self) -> int: ...
        def __iter__(self) -> Iterator[bytes]: ...
        def __enter__(self) -> 'HTTPResponse': ...
        def __exit__(self, exc_type: Optional[type],
                     exc_val: Optional[Exception],
                     exc_tb: Optional[types.TracebackType]) -> bool: ...

class HTTPConnection:
    if sys.version_info >= (3, 4):
        def __init__(self,
                     host: str, port: Optional[int] = ...,
                     timeout: int = ...,
                     source_address: Optional[Tuple[str, int]] = ...) \
                     -> None: ...
    else:
        def __init__(self,
                     host: str, port: Optional[int] = ...,
                     strict: bool = ..., timeout: int = ...,
                     source_address: Optional[Tuple[str, int]] = ...) \
                             -> None: ...
    def request(self, method: str, url: str,
                body: Optional[_DataType] = ...,
                headers: Mapping[str, str] = ...) -> None: ...
    def getresponse(self) -> HTTPResponse: ...
    def set_debuglevel(self, level: int) -> None: ...
    def set_tunnel(self, host: str, port: Optional[int] = ...,
                   headers: Optional[Mapping[str, str]] = ...) -> None: ...
    def connect(self) -> None: ...
    def close(self) -> None: ...
    def putrequest(self, request: str, selector: str, skip_host: bool = ...,
                   skip_accept_encoding: bool = ...) -> None: ...
    def putheader(self, header: str, *argument: str) -> None: ...
    def endheaders(self, message_body: Optional[_DataType] = ...) -> None: ...
    def send(self, data: _DataType) -> None: ...

class HTTPSConnection(HTTPConnection):
    if sys.version_info >= (3, 4):
        def __init__(self,
                     host: str, port: Optional[int] = ...,
                     key_file: Optional[str] = ...,
                     cert_file: Optional[str] = ...,
                     timeout: int = ...,
                     source_address: Optional[Tuple[str, int]] = ...,
                     *, context: Optional[ssl.SSLContext] = ...,
                     check_hostname: Optional[bool] = ...) -> None: ...
    else:
        def __init__(self,
                     host: str, port: Optional[int] = ...,
                     key_file: Optional[str] = ...,
                     cert_file: Optional[str] = ...,
                     strict: bool = ..., timeout: int = ...,
                     source_address: Optional[Tuple[str, int]] = ...,
                     *, context: Optional[ssl.SSLContext] = ...,
                     check_hostname: Optional[bool] = ...) -> None: ...

class HTTPException(Exception): ...

class NotConnected(HTTPException): ...
class InvalidURL(HTTPException): ...
class UnknownProtocol(HTTPException): ...
class UnknownTransferEncoding(HTTPException): ...
class UnimplementedFileMode(HTTPException): ...
class IncompleteRead(HTTPException): ...

class ImproperConnectionState(HTTPException): ...
class CannotSendRequest(ImproperConnectionState): ...
class CannotSendHeader(ImproperConnectionState): ...
class ResponseNotReady(ImproperConnectionState): ...

class BadStatusLine(HTTPException): ...
class LineTooLong(HTTPException): ...

if sys.version_info >= (3, 5):
    class RemoteDisconnected(ConnectionResetError, BadStatusLine): ...
