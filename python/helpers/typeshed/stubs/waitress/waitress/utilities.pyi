from _typeshed.wsgi import StartResponse
from collections.abc import Iterator, Mapping, Sequence
from logging import Logger
from re import Match, Pattern
from typing import Any

logger: Logger
queue_logger: Logger

def find_double_newline(s: bytes) -> int: ...
def concat(*args: Any) -> str: ...
def join(seq: Any, field: str = " ") -> str: ...
def group(s: Any) -> str: ...

short_days: Sequence[str]
long_days: Sequence[str]
short_day_reg: str
long_day_reg: str
daymap: Mapping[str, int]
hms_reg: str
months: Sequence[str]
monmap: Mapping[str, int]
months_reg: str
rfc822_date: str
rfc822_reg: Pattern[Any]

def unpack_rfc822(m: Match[Any]) -> tuple[int, int, int, int, int, int, int, int, int]: ...

rfc850_date: str
rfc850_reg: Pattern[Any]

def unpack_rfc850(m: Match[Any]) -> tuple[int, int, int, int, int, int, int, int, int]: ...

weekdayname: Sequence[str]
monthname: Sequence[str]

def build_http_date(when: int) -> str: ...
def parse_http_date(d: str) -> int: ...
def undquote(value: str) -> str: ...
def cleanup_unix_socket(path: str) -> None: ...

class Error:
    code: int
    reason: str
    body: str
    def __init__(self, body: str) -> None: ...
    def to_response(self) -> tuple[str, Sequence[tuple[str, str]], str]: ...
    def wsgi_response(self, environ: Any, start_response: StartResponse) -> Iterator[str]: ...

class BadRequest(Error):
    code: int
    reason: str

class RequestHeaderFieldsTooLarge(BadRequest):
    code: int
    reason: str

class RequestEntityTooLarge(BadRequest):
    code: int
    reason: str

class InternalServerError(Error):
    code: int
    reason: str

class ServerNotImplemented(Error):
    code: int
    reason: str
