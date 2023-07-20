from _typeshed import OpenBinaryModeReading, OpenBinaryModeWriting, OpenTextModeReading, OpenTextModeWriting, SupportsWrite
from re import Pattern
from typing import Any, ClassVar
from typing_extensions import Literal

from docutils import TransformSpec

__docformat__: str

class InputError(IOError): ...
class OutputError(IOError): ...

def check_encoding(stream: Any, encoding: str) -> bool | None: ...
def error_string(err: BaseException) -> str: ...

class Input(TransformSpec):
    component_type: ClassVar[str]
    default_source_path: ClassVar[str | None]
    def read(self) -> Any: ...
    def decode(self, data: str | bytes) -> str: ...
    coding_slug: ClassVar[Pattern[bytes]]
    byte_order_marks: ClassVar[tuple[tuple[bytes, str], ...]]
    def determine_encoding_from_data(self, data: str | bytes) -> str | None: ...
    def isatty(self) -> bool: ...

class Output(TransformSpec):
    component_type: ClassVar[str]
    default_destination_path: ClassVar[str | None]
    def __init__(
        self,
        destination: Any | None = ...,
        destination_path: Any | None = ...,
        encoding: str | None = ...,
        error_handler: str = ...,
    ) -> None: ...
    def write(self, data: str) -> Any: ...  # returns bytes or str
    def encode(self, data: str) -> Any: ...  # returns bytes or str

class ErrorOutput:
    def __init__(
        self,
        destination: str | SupportsWrite[str] | SupportsWrite[bytes] | Literal[False] | None = ...,
        encoding: str | None = ...,
        encoding_errors: str = ...,
        decoding_errors: str = ...,
    ) -> None: ...
    def write(self, data: str | bytes | Exception) -> None: ...
    def close(self) -> None: ...
    def isatty(self) -> bool: ...

class FileInput(Input):
    def __init__(
        self,
        source: Any | None = ...,
        source_path: Any | None = ...,
        encoding: str | None = ...,
        error_handler: str = ...,
        autoclose: bool = ...,
        mode: OpenTextModeReading | OpenBinaryModeReading = ...,
    ) -> None: ...
    def read(self) -> str: ...
    def readlines(self) -> list[str]: ...
    def close(self) -> None: ...

class FileOutput(Output):
    mode: ClassVar[OpenTextModeWriting | OpenBinaryModeWriting]
    def __getattr__(self, name: str) -> Any: ...  # incomplete

class BinaryFileOutput(FileOutput): ...

class StringInput(Input):
    default_source_path: ClassVar[str]

class StringOutput(Output):
    default_destination_path: ClassVar[str]
    destination: str | bytes  # only defined after call to write()

class NullInput(Input):
    default_source_path: ClassVar[str]
    def read(self) -> str: ...

class NullOutput(Output):
    default_destination_path: ClassVar[str]
    def write(self, data: object) -> None: ...

class DocTreeInput(Input):
    default_source_path: ClassVar[str]
