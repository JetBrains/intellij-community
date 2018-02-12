from typing import Any
from yaml.error import YAMLError

class ReaderError(YAMLError):
    name = ...  # type: Any
    character = ...  # type: Any
    position = ...  # type: Any
    encoding = ...  # type: Any
    reason = ...  # type: Any
    def __init__(self, name, position, character, encoding, reason) -> None: ...

class Reader:
    name = ...  # type: Any
    stream = ...  # type: Any
    stream_pointer = ...  # type: Any
    eof = ...  # type: Any
    buffer = ...  # type: Any
    pointer = ...  # type: Any
    raw_buffer = ...  # type: Any
    raw_decode = ...  # type: Any
    encoding = ...  # type: Any
    index = ...  # type: Any
    line = ...  # type: Any
    column = ...  # type: Any
    def __init__(self, stream) -> None: ...
    def peek(self, index=...): ...
    def prefix(self, length=...): ...
    def forward(self, length=...): ...
    def get_mark(self): ...
    def determine_encoding(self): ...
    NON_PRINTABLE = ...  # type: Any
    def check_printable(self, data): ...
    def update(self, length): ...
    def update_raw(self, size=...): ...
