from typing import IO

from django.core.files.base import File
from typing_extensions import Self

class UploadedFile(File):
    content_type: str | None
    charset: str | None
    content_type_extra: dict[str, str] | None
    size: int | None  # type: ignore[assignment]
    name: str | None
    def __init__(
        self,
        file: IO | None = ...,
        name: str | None = ...,
        content_type: str | None = ...,
        size: int | None = ...,
        charset: str | None = ...,
        content_type_extra: dict[str, str] | None = ...,
    ) -> None: ...

class TemporaryUploadedFile(UploadedFile):
    def __init__(
        self,
        name: str,
        content_type: str | None,
        size: int | None,
        charset: str | None,
        content_type_extra: dict[str, str] | None = ...,
    ) -> None: ...
    def temporary_file_path(self) -> str: ...

class InMemoryUploadedFile(UploadedFile):
    field_name: str | None
    def __init__(
        self,
        file: IO,
        field_name: str | None,
        name: str | None,
        content_type: str | None,
        size: int | None,
        charset: str | None,
        content_type_extra: dict[str, str] | None = ...,
    ) -> None: ...
    def open(self, mode: str | None = ...) -> Self: ...  # type: ignore[override]

class SimpleUploadedFile(InMemoryUploadedFile):
    def __init__(self, name: str, content: bytes | None, content_type: str = ...) -> None: ...
    @classmethod
    def from_dict(cls, file_dict: dict[str, str | bytes]) -> Self: ...
