from collections.abc import Iterator
from typing import Any

from django.core.serializers import base
from django.core.serializers.base import DeserializedObject
from django.db.models.base import Model

class Serializer(base.Serializer):
    objects: list[Any]
    def get_dump_object(self, obj: Model) -> dict[str, Any]: ...

def Deserializer(
    object_list: list[dict[str, Any]], *, using: str = ..., ignorenonexistent: bool = ..., **options: Any
) -> Iterator[DeserializedObject]: ...
