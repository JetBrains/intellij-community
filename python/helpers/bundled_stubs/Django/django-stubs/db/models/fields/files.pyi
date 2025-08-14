from collections.abc import Callable, Iterable
from typing import Any, Protocol, TypeVar, overload, type_check_only

from django.core import validators  # due to weird mypy.stubtest error
from django.core.files.base import File
from django.core.files.images import ImageFile
from django.core.files.storage import Storage
from django.db.models.base import Model
from django.db.models.expressions import Expression
from django.db.models.fields import NOT_PROVIDED, Field, _ErrorMessagesMapping
from django.db.models.query_utils import DeferredAttribute
from django.db.models.utils import AltersData
from django.utils._os import _PathCompatible
from django.utils.choices import _Choices
from django.utils.functional import _StrOrPromise
from typing_extensions import Self

class FieldFile(File, AltersData):
    instance: Model
    field: FileField
    storage: Storage
    name: str | None
    def __init__(self, instance: Model, field: FileField, name: str | None) -> None: ...
    file: Any
    @property
    def path(self) -> str: ...
    @property
    def url(self) -> str: ...
    @property
    def size(self) -> int: ...
    def open(self, mode: str = "rb") -> Self: ...  # type: ignore[override]
    def save(self, name: str, content: File, save: bool = True) -> None: ...
    def delete(self, save: bool = True) -> None: ...
    @property
    def closed(self) -> bool: ...
    def __getstate__(self) -> dict[str, Any]: ...
    def __setstate__(self, state: dict[str, Any]) -> None: ...
    def __eq__(self, other: object) -> bool: ...
    def __hash__(self) -> int: ...

class FileDescriptor(DeferredAttribute):
    field: FileField
    def __set__(self, instance: Model, value: Any | None) -> None: ...
    def __get__(self, instance: Model | None, cls: type[Model] | None = None) -> FieldFile | FileDescriptor: ...

_M = TypeVar("_M", bound=Model, contravariant=True)

@type_check_only
class _UploadToCallable(Protocol[_M]):
    def __call__(self, instance: _M, filename: str, /) -> _PathCompatible: ...

class FileField(Field):
    storage: Storage
    upload_to: _PathCompatible | _UploadToCallable
    def __init__(
        self,
        verbose_name: _StrOrPromise | None = None,
        name: str | None = None,
        upload_to: _PathCompatible | _UploadToCallable = "",
        storage: Storage | Callable[[], Storage] | None = None,
        *,
        max_length: int | None = ...,
        unique: bool = ...,
        blank: bool = ...,
        null: bool = ...,
        db_index: bool = ...,
        default: Any = ...,
        db_default: type[NOT_PROVIDED] | Expression | str = ...,
        editable: bool = ...,
        auto_created: bool = ...,
        serialize: bool = ...,
        unique_for_date: str | None = ...,
        unique_for_month: str | None = ...,
        unique_for_year: str | None = ...,
        choices: _Choices | None = ...,
        help_text: _StrOrPromise = ...,
        db_column: str | None = ...,
        db_comment: str | None = ...,
        db_tablespace: str | None = ...,
        validators: Iterable[validators._ValidatorCallable] = ...,
        error_messages: _ErrorMessagesMapping | None = ...,
    ) -> None: ...
    # class access
    @overload
    def __get__(self, instance: None, owner: Any) -> FileDescriptor: ...
    # Model instance access
    @overload
    def __get__(self, instance: Model, owner: Any) -> Any: ...
    # non-Model instances
    @overload
    def __get__(self, instance: Any, owner: Any) -> Self: ...
    def generate_filename(self, instance: Model | None, filename: _PathCompatible) -> str: ...

class ImageFileDescriptor(FileDescriptor):
    field: ImageField
    def __set__(self, instance: Model, value: str | None) -> None: ...

class ImageFieldFile(ImageFile, FieldFile):
    field: ImageField
    def delete(self, save: bool = True) -> None: ...

class ImageField(FileField):
    def __init__(
        self,
        verbose_name: _StrOrPromise | None = None,
        name: str | None = None,
        width_field: str | None = None,
        height_field: str | None = None,
        **kwargs: Any,
    ) -> None: ...
    # class access
    @overload
    def __get__(self, instance: None, owner: Any) -> ImageFileDescriptor: ...
    # Model instance access
    @overload
    def __get__(self, instance: Model, owner: Any) -> Any: ...
    # non-Model instances
    @overload
    def __get__(self, instance: Any, owner: Any) -> Self: ...
    def update_dimension_fields(self, instance: Model, force: bool = False, *args: Any, **kwargs: Any) -> None: ...
