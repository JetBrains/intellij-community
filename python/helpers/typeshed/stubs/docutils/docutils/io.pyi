from _typeshed import OpenBinaryModeReading, OpenBinaryModeWriting, OpenTextModeReading, OpenTextModeWriting
from typing import Any, ClassVar

from docutils import TransformSpec

__docformat__: str

class InputError(IOError): ...
class OutputError(IOError): ...

def check_encoding(stream: Any, encoding: str) -> bool | None: ...

class Input(TransformSpec):
    component_type: ClassVar[str]
    default_source_path: ClassVar[str | None]
    def read(self) -> Any: ...
    def __getattr__(self, name: str) -> Any: ...  # incomplete

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
