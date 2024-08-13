from collections.abc import Iterator
from typing import IO, Any

from django.core.serializers.base import DeserializedObject
from django.core.serializers.python import Serializer as PythonSerializer
from django.db.models.fields import Field
from yaml import CSafeDumper as SafeDumper
from yaml import MappingNode, ScalarNode

class DjangoSafeDumper(SafeDumper):
    def represent_decimal(self, data: Any) -> ScalarNode: ...
    def represent_ordered_dict(self, data: Any) -> MappingNode: ...

class Serializer(PythonSerializer):
    internal_use_only: bool
    def handle_field(self, obj: Any, field: Field) -> None: ...
    def end_serialization(self) -> None: ...
    def getvalue(self) -> Any: ...

def Deserializer(
    stream_or_string: bytes | str | IO[bytes] | IO[str], **options: Any
) -> Iterator[DeserializedObject]: ...
