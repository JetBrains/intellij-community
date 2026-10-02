from _typeshed import Incomplete, SupportsRead
from re import Pattern
from typing import Final, TypeAlias

from yaml.error import Mark, YAMLError

_ReadStream: TypeAlias = str | bytes | SupportsRead[str] | SupportsRead[bytes]

class ReaderError(YAMLError):
    name: Incomplete
    character: Incomplete
    position: Incomplete
    encoding: Incomplete
    reason: Incomplete
    def __init__(self, name, position, character, encoding, reason) -> None: ...

class Reader:
    name: Incomplete
    stream: SupportsRead[str] | SupportsRead[bytes] | None
    stream_pointer: Incomplete
    eof: Incomplete
    buffer: Incomplete
    pointer: Incomplete
    raw_buffer: Incomplete
    raw_decode: Incomplete
    encoding: Incomplete
    index: Incomplete
    line: Incomplete
    column: Incomplete
    def __init__(self, stream: _ReadStream) -> None: ...
    def peek(self, index: int = 0): ...
    def prefix(self, length: int = 1): ...
    def forward(self, length: int = 1) -> None: ...
    def get_mark(self) -> Mark: ...
    def determine_encoding(self) -> None: ...
    NON_PRINTABLE: Final[Pattern[str]]
    def check_printable(self, data: str) -> None: ...
    def update(self, length: int) -> None: ...
    def update_raw(self, size: int = 4096) -> None: ...

__all__ = ["Reader", "ReaderError"]
