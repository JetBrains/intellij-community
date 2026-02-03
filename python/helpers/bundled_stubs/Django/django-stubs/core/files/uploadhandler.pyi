from typing import IO, Any

from django.core.files.uploadedfile import TemporaryUploadedFile, UploadedFile
from django.http.request import HttpRequest, QueryDict
from django.utils.datastructures import MultiValueDict

class UploadFileException(Exception): ...

class StopUpload(UploadFileException):
    connection_reset: bool
    def __init__(self, connection_reset: bool = False) -> None: ...

class SkipFile(UploadFileException): ...
class StopFutureHandlers(UploadFileException): ...

class FileUploadHandler:
    chunk_size: int
    file_name: str | None
    content_type: str | None
    content_length: int | None
    charset: str | None
    content_type_extra: dict[str, bytes] | None
    request: HttpRequest | None
    field_name: str
    def __init__(self, request: HttpRequest | None = None) -> None: ...
    def handle_raw_input(
        self,
        input_data: IO[bytes],
        META: dict[str, str],
        content_length: int,
        boundary: str,
        encoding: str | None = None,
    ) -> tuple[QueryDict, MultiValueDict[str, UploadedFile]] | None: ...
    def new_file(
        self,
        field_name: str,
        file_name: str,
        content_type: str,
        content_length: int | None,
        charset: str | None = None,
        content_type_extra: dict[str, bytes] | None = None,
    ) -> None: ...
    def receive_data_chunk(self, raw_data: bytes, start: int) -> bytes | None: ...
    def file_complete(self, file_size: int) -> UploadedFile | None: ...
    def upload_complete(self) -> None: ...
    def upload_interrupted(self) -> None: ...

class TemporaryFileUploadHandler(FileUploadHandler):
    file: TemporaryUploadedFile
    def new_file(
        self,
        field_name: str,
        file_name: str,
        content_type: str,
        content_length: int | None,
        charset: str | None = ...,
        content_type_extra: dict[str, bytes] | None = ...,
    ) -> None: ...
    def receive_data_chunk(self, raw_data: bytes, start: int) -> bytes | None: ...
    def file_complete(self, file_size: int) -> UploadedFile | None: ...
    def upload_interrupted(self) -> None: ...

class MemoryFileUploadHandler(FileUploadHandler):
    activated: bool
    file: IO[bytes]
    def handle_raw_input(
        self,
        input_data: IO[bytes],
        META: dict[str, str],
        content_length: int,
        boundary: str,
        encoding: str | None = None,
    ) -> tuple[QueryDict, MultiValueDict[str, UploadedFile]] | None: ...
    def new_file(
        self,
        field_name: str,
        file_name: str,
        content_type: str,
        content_length: int | None,
        charset: str | None = ...,
        content_type_extra: dict[str, bytes] | None = ...,
    ) -> None: ...
    def receive_data_chunk(self, raw_data: bytes, start: int) -> bytes | None: ...
    def file_complete(self, file_size: int) -> UploadedFile | None: ...

def load_handler(path: str, *args: Any, **kwargs: Any) -> FileUploadHandler: ...

__all__ = [
    "FileUploadHandler",
    "MemoryFileUploadHandler",
    "SkipFile",
    "StopFutureHandlers",
    "StopUpload",
    "TemporaryFileUploadHandler",
    "UploadFileException",
    "load_handler",
]
