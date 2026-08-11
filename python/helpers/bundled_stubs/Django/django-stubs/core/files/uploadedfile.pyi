from typing import IO, Any

from django.core.files.base import File
from django.utils.functional import cached_property
from typing_extensions import Self, TypeVar, override

# `typing.AnyStr` is deprecated from 3.16+, so we define our own equivalent constrained TypeVar.
_AnyStr = TypeVar("_AnyStr", str, bytes)

class UploadedFile(File[_AnyStr]):
    content_type: str | None
    charset: str | None
    content_type_extra: dict[str, bytes] | None
    @override
    @cached_property
    def size(self) -> int | None: ...  # type: ignore[override]
    name: str | None
    def __init__(
        self,
        file: IO[_AnyStr] | None = None,
        name: str | None = None,
        content_type: str | None = None,
        size: int | None = None,
        charset: str | None = None,
        content_type_extra: dict[str, bytes] | None = None,
    ) -> None: ...

class TemporaryUploadedFile(UploadedFile[Any]):
    def __init__(
        self,
        name: str,
        content_type: str | None,
        size: int | None,
        charset: str | None,
        content_type_extra: dict[str, bytes] | None = None,
    ) -> None: ...
    def temporary_file_path(self) -> str: ...

class InMemoryUploadedFile(UploadedFile[Any]):
    field_name: str | None
    def __init__(
        self,
        file: IO[Any],
        field_name: str | None,
        name: str | None,
        content_type: str | None,
        size: int | None,
        charset: str | None,
        content_type_extra: dict[str, bytes] | None = None,
    ) -> None: ...
    @override
    def open(self, mode: str | None = None) -> Self: ...  # type: ignore[override]

class SimpleUploadedFile(InMemoryUploadedFile):
    def __init__(self, name: str, content: bytes | None, content_type: str = "text/plain") -> None: ...
    @classmethod
    def from_dict(cls, file_dict: dict[str, str | bytes]) -> Self: ...

__all__ = (
    "InMemoryUploadedFile",
    "SimpleUploadedFile",
    "TemporaryUploadedFile",
    "UploadedFile",
)
