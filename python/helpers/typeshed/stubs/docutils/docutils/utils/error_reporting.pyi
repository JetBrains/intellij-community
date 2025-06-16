from io import TextIOWrapper
from typing import TextIO
from typing_extensions import TypeAlias, deprecated

unicode = str

_DataType: TypeAlias = str | Exception

@deprecated("`docutils.utils.error_reporting` module is deprecated and will be removed in Docutils 0.21 or later.")
class SafeString:
    data: object
    encoding: str
    encoding_errors: str
    decoding_errors: str
    def __init__(
        self,
        data: object,
        encoding: str | None = None,
        encoding_errors: str = "backslashreplace",
        decoding_errors: str = "replace",
    ) -> None: ...
    def __unicode__(self) -> str: ...

@deprecated("`docutils.utils.error_reporting` module is deprecated and will be removed in Docutils 0.21 or later.")
class ErrorString(SafeString): ...

@deprecated("`docutils.utils.error_reporting` module is deprecated and will be removed in Docutils 0.21 or later.")
class ErrorOutput:
    stream: TextIO | TextIOWrapper
    encoding: str
    encoding_errors: str
    decoding_errors: str
    def __init__(
        self,
        stream=None,
        encoding: str | None = None,
        encoding_errors: str = "backslashreplace",
        decoding_errors: str = "replace",
    ) -> None: ...
    def write(self, data: _DataType) -> None: ...
    def close(self) -> None: ...
