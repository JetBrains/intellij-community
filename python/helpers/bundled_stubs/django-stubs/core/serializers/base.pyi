from collections.abc import Collection, Iterable, Sequence
from typing import IO, Any

from django.db.models.base import Model
from django.db.models.fields import Field
from django.db.models.fields.related import ForeignKey, ManyToManyField

DEFER_FIELD: object

class SerializerDoesNotExist(KeyError): ...
class SerializationError(Exception): ...

class DeserializationError(Exception):
    @classmethod
    def WithData(
        cls, original_exc: Exception, model: str, fk: int | str, field_value: Sequence[str] | str | None
    ) -> DeserializationError: ...

class M2MDeserializationError(Exception):
    original_exc: Exception
    pk: Any
    def __init__(self, original_exc: Exception, pk: Any) -> None: ...

class ProgressBar:
    progress_width: int
    output: IO[str] | None
    total_count: int
    prev_done: int
    def __init__(self, output: IO[str] | None, total_count: int) -> None: ...
    def update(self, count: int) -> None: ...

class Serializer:
    internal_use_only: bool
    progress_class: type[ProgressBar]
    stream_class: type[IO[str]]
    options: dict[str, Any]
    stream: IO[str]
    selected_fields: Collection[str] | None
    use_natural_foreign_keys: bool
    use_natural_primary_keys: bool
    first: bool
    def serialize(
        self,
        queryset: Iterable[Model],
        *,
        stream: IO[str] | None = ...,
        fields: Collection[str] | None = ...,
        use_natural_foreign_keys: bool = ...,
        use_natural_primary_keys: bool = ...,
        progress_output: IO[str] | None = ...,
        object_count: int = ...,
        **options: Any,
    ) -> Any: ...
    def start_serialization(self) -> None: ...
    def end_serialization(self) -> None: ...
    def start_object(self, obj: Any) -> None: ...
    def end_object(self, obj: Any) -> None: ...
    def handle_field(self, obj: Any, field: Any) -> None: ...
    def handle_fk_field(self, obj: Any, field: Any) -> None: ...
    def handle_m2m_field(self, obj: Any, field: Any) -> None: ...
    def getvalue(self) -> bytes | str | None: ...

class Deserializer:
    options: dict[str, Any]
    stream: IO[str] | IO[bytes]
    def __init__(self, stream_or_string: bytes | str | IO[bytes] | IO[str], **options: Any) -> None: ...
    def __iter__(self) -> Deserializer: ...
    def __next__(self) -> Any: ...

class DeserializedObject:
    object: Model
    m2m_data: dict[str, Sequence[Any]] | None
    deferred_fields: dict[Field, Any]
    def __init__(
        self,
        obj: Model,
        m2m_data: dict[str, Sequence[Any]] | None = ...,
        deferred_fields: dict[Field, Any] | None = ...,
    ) -> None: ...
    def save(self, save_m2m: bool = ..., using: str | None = ..., **kwargs: Any) -> None: ...
    def save_deferred_fields(self, using: str | None = ...) -> None: ...

def build_instance(Model: type[Model], data: dict[str, Any], db: str) -> Model: ...
def deserialize_m2m_values(
    field: ManyToManyField, field_value: Iterable[Any], using: str | None, handle_forward_references: bool
) -> Sequence[Any] | object: ...
def deserialize_fk_value(
    field: ForeignKey, field_value: Any, using: str | None, handle_forward_references: bool
) -> Any | object: ...
