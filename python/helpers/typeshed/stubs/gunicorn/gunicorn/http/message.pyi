import io
import re

from gunicorn.config import Config
from gunicorn.http.body import Body
from gunicorn.http.unreader import Unreader

from .._types import _AddressType

MAX_REQUEST_LINE: int
MAX_HEADERS: int
DEFAULT_MAX_HEADERFIELD_SIZE: int
RFC9110_5_6_2_TOKEN_SPECIALS: str
TOKEN_RE: re.Pattern[str]
METHOD_BADCHAR_RE: re.Pattern[str]
VERSION_RE: re.Pattern[str]
RFC9110_5_5_INVALID_AND_DANGEROUS: re.Pattern[str]

class Message:
    cfg: Config
    unreader: Unreader
    peer_addr: _AddressType
    remote_addr: _AddressType
    version: tuple[int, int] | None
    headers: list[tuple[str, str]]
    trailers: list[tuple[str, str]]
    body: Body | None
    scheme: str
    must_close: bool
    limit_request_fields: int
    limit_request_field_size: int
    max_buffer_headers: int

    def __init__(self, cfg: Config, unreader: Unreader, peer_addr: _AddressType) -> None: ...
    def force_close(self) -> None: ...
    def parse(self, unreader: Unreader) -> bytes: ...
    def parse_headers(self, data: bytes, from_trailer: bool = False) -> list[tuple[str, str]]: ...
    def set_body_reader(self) -> None: ...
    def should_close(self) -> bool: ...

class Request(Message):
    method: str | None
    uri: str | None
    path: str | None
    query: str | None
    fragment: str | None
    limit_request_line: int
    req_number: int
    proxy_protocol_info: dict[str, str | int] | None

    def __init__(self, cfg: Config, unreader: Unreader, peer_addr: _AddressType, req_number: int = 1) -> None: ...
    def get_data(self, unreader: Unreader, buf: io.BytesIO, stop: bool = False) -> None: ...
    def parse(self, unreader: Unreader) -> bytes: ...
    def read_line(self, unreader: Unreader, buf: io.BytesIO, limit: int = 0) -> tuple[bytes, bytes]: ...
    def proxy_protocol(self, line: str) -> bool: ...
    def proxy_protocol_access_check(self) -> None: ...
    def parse_proxy_protocol(self, line: str) -> None: ...
    def parse_request_line(self, line_bytes: bytes) -> None: ...
    def set_body_reader(self) -> None: ...
