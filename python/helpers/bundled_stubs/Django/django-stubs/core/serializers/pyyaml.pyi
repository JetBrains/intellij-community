from typing import IO, Any

from django.core.serializers.base import Deserializer as PythonDeserializer
from django.core.serializers.python import Serializer as PythonSerializer
from django.db.models.fields import Field
from typing_extensions import override
from yaml import CSafeDumper as SafeDumper
from yaml import MappingNode, ScalarNode

class DjangoSafeDumper(SafeDumper):
    def represent_decimal(self, data: Any) -> ScalarNode: ...
    def represent_ordered_dict(self, data: Any) -> MappingNode: ...

class Serializer(PythonSerializer):
    internal_use_only: bool
    @override
    def handle_field(self, obj: Any, field: Field) -> None: ...
    @override
    def end_serialization(self) -> None: ...
    @override
    def getvalue(self) -> Any: ...

class Deserializer(PythonDeserializer):
    def __init__(self, stream_or_string: bytes | str | IO[bytes] | IO[str], **options: Any) -> None: ...
