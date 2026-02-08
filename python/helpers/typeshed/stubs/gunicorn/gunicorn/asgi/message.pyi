import re
from typing import Final, Literal
from typing_extensions import Self

from gunicorn.asgi.unreader import AsyncUnreader
from gunicorn.config import Config

from .._types import _AddressType

MAX_REQUEST_LINE: Final = 8190
MAX_HEADERS: Final = 32768
DEFAULT_MAX_HEADERFIELD_SIZE: Final = 8190
RFC9110_5_6_2_TOKEN_SPECIALS: Final = r"!#$%&'*+-.^_`|~"
TOKEN_RE: Final[re.Pattern[str]]
METHOD_BADCHAR_RE: Final[re.Pattern[str]]
VERSION_RE: Final[re.Pattern[str]]
RFC9110_5_5_INVALID_AND_DANGEROUS: Final[re.Pattern[str]]

class AsyncRequest:
    cfg: Config
    unreader: AsyncUnreader
    peer_addr: _AddressType
    remote_addr: _AddressType
    req_number: int
    version: tuple[int, int] | None
    method: str | None
    uri: str | None
    path: str | None
    query: str | None
    fragment: str | None
    headers: list[tuple[str, str]]
    trailers: list[tuple[str, str]]
    scheme: Literal["https", "http"]
    must_close: bool
    proxy_protocol_info: dict[str, str | int | None] | None  # TODO: Use TypedDict
    limit_request_line: int
    limit_request_fields: int
    limit_request_field_size: int
    max_buffer_headers: int
    content_length: int | None
    chunked: bool

    def __init__(self, cfg: Config, unreader: AsyncUnreader, peer_addr: _AddressType, req_number: int = 1) -> None: ...
    @classmethod
    async def parse(cls, cfg: Config, unreader: AsyncUnreader, peer_addr: _AddressType, req_number: int = 1) -> Self: ...
    def force_close(self) -> None: ...
    def should_close(self) -> bool: ...
    def get_header(self, name: str) -> str: ...
    async def read_body(self, size: int = 8192) -> bytes: ...
    async def drain_body(self) -> None: ...
