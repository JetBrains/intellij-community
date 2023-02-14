import sys
from _typeshed import Self
from collections.abc import Sequence
from email.message import Message as _Message
from re import Pattern
from socket import socket
from ssl import SSLContext
from types import TracebackType
from typing import Any, Protocol, overload
from typing_extensions import TypeAlias

__all__ = [
    "SMTPException",
    "SMTPServerDisconnected",
    "SMTPResponseException",
    "SMTPSenderRefused",
    "SMTPRecipientsRefused",
    "SMTPDataError",
    "SMTPConnectError",
    "SMTPHeloError",
    "SMTPAuthenticationError",
    "quoteaddr",
    "quotedata",
    "SMTP",
    "SMTP_SSL",
    "SMTPNotSupportedError",
]

_Reply: TypeAlias = tuple[int, bytes]
_SendErrs: TypeAlias = dict[str, _Reply]
# Should match source_address for socket.create_connection
_SourceAddress: TypeAlias = tuple[bytearray | bytes | str, int]

SMTP_PORT: int
SMTP_SSL_PORT: int
CRLF: str
bCRLF: bytes

OLDSTYLE_AUTH: Pattern[str]

class SMTPException(OSError): ...
class SMTPNotSupportedError(SMTPException): ...
class SMTPServerDisconnected(SMTPException): ...

class SMTPResponseException(SMTPException):
    smtp_code: int
    smtp_error: bytes | str
    args: tuple[int, bytes | str] | tuple[int, bytes, str]
    def __init__(self, code: int, msg: bytes | str) -> None: ...

class SMTPSenderRefused(SMTPResponseException):
    smtp_code: int
    smtp_error: bytes
    sender: str
    args: tuple[int, bytes, str]
    def __init__(self, code: int, msg: bytes, sender: str) -> None: ...

class SMTPRecipientsRefused(SMTPException):
    recipients: _SendErrs
    args: tuple[_SendErrs]
    def __init__(self, recipients: _SendErrs) -> None: ...

class SMTPDataError(SMTPResponseException): ...
class SMTPConnectError(SMTPResponseException): ...
class SMTPHeloError(SMTPResponseException): ...
class SMTPAuthenticationError(SMTPResponseException): ...

def quoteaddr(addrstring: str) -> str: ...
def quotedata(data: str) -> str: ...

class _AuthObject(Protocol):
    @overload
    def __call__(self, challenge: None = ...) -> str | None: ...
    @overload
    def __call__(self, challenge: bytes) -> str: ...

class SMTP:
    debuglevel: int
    sock: socket | None
    # Type of file should match what socket.makefile() returns
    file: Any | None
    helo_resp: bytes | None
    ehlo_msg: str
    ehlo_resp: bytes | None
    does_esmtp: bool
    default_port: int
    timeout: float
    esmtp_features: dict[str, str]
    command_encoding: str
    source_address: _SourceAddress | None
    local_hostname: str
    def __init__(
        self,
        host: str = ...,
        port: int = ...,
        local_hostname: str | None = ...,
        timeout: float = ...,
        source_address: _SourceAddress | None = ...,
    ) -> None: ...
    def __enter__(self: Self) -> Self: ...
    def __exit__(
        self, exc_type: type[BaseException] | None, exc_value: BaseException | None, tb: TracebackType | None
    ) -> None: ...
    def set_debuglevel(self, debuglevel: int) -> None: ...
    def connect(self, host: str = ..., port: int = ..., source_address: _SourceAddress | None = ...) -> _Reply: ...
    def send(self, s: bytes | str) -> None: ...
    def putcmd(self, cmd: str, args: str = ...) -> None: ...
    def getreply(self) -> _Reply: ...
    def docmd(self, cmd: str, args: str = ...) -> _Reply: ...
    def helo(self, name: str = ...) -> _Reply: ...
    def ehlo(self, name: str = ...) -> _Reply: ...
    def has_extn(self, opt: str) -> bool: ...
    def help(self, args: str = ...) -> bytes: ...
    def rset(self) -> _Reply: ...
    def noop(self) -> _Reply: ...
    def mail(self, sender: str, options: Sequence[str] = ...) -> _Reply: ...
    def rcpt(self, recip: str, options: Sequence[str] = ...) -> _Reply: ...
    def data(self, msg: bytes | str) -> _Reply: ...
    def verify(self, address: str) -> _Reply: ...
    vrfy = verify
    def expn(self, address: str) -> _Reply: ...
    def ehlo_or_helo_if_needed(self) -> None: ...
    user: str
    password: str
    def auth(self, mechanism: str, authobject: _AuthObject, *, initial_response_ok: bool = ...) -> _Reply: ...
    @overload
    def auth_cram_md5(self, challenge: None = ...) -> None: ...
    @overload
    def auth_cram_md5(self, challenge: bytes) -> str: ...
    def auth_plain(self, challenge: bytes | None = ...) -> str: ...
    def auth_login(self, challenge: bytes | None = ...) -> str: ...
    def login(self, user: str, password: str, *, initial_response_ok: bool = ...) -> _Reply: ...
    def starttls(self, keyfile: str | None = ..., certfile: str | None = ..., context: SSLContext | None = ...) -> _Reply: ...
    def sendmail(
        self,
        from_addr: str,
        to_addrs: str | Sequence[str],
        msg: bytes | str,
        mail_options: Sequence[str] = ...,
        rcpt_options: Sequence[str] = ...,
    ) -> _SendErrs: ...
    def send_message(
        self,
        msg: _Message,
        from_addr: str | None = ...,
        to_addrs: str | Sequence[str] | None = ...,
        mail_options: Sequence[str] = ...,
        rcpt_options: Sequence[str] = ...,
    ) -> _SendErrs: ...
    def close(self) -> None: ...
    def quit(self) -> _Reply: ...

class SMTP_SSL(SMTP):
    default_port: int
    keyfile: str | None
    certfile: str | None
    context: SSLContext
    def __init__(
        self,
        host: str = ...,
        port: int = ...,
        local_hostname: str | None = ...,
        keyfile: str | None = ...,
        certfile: str | None = ...,
        timeout: float = ...,
        source_address: _SourceAddress | None = ...,
        context: SSLContext | None = ...,
    ) -> None: ...

LMTP_PORT: int

class LMTP(SMTP):
    if sys.version_info >= (3, 9):
        def __init__(
            self,
            host: str = ...,
            port: int = ...,
            local_hostname: str | None = ...,
            source_address: _SourceAddress | None = ...,
            timeout: float = ...,
        ) -> None: ...
    else:
        def __init__(
            self, host: str = ..., port: int = ..., local_hostname: str | None = ..., source_address: _SourceAddress | None = ...
        ) -> None: ...
