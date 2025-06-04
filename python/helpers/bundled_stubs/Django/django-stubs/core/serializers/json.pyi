import json
from collections.abc import Iterator
from typing import IO, Any

from django.core.serializers.base import DeserializedObject
from django.core.serializers.python import Serializer as PythonSerializer

class Serializer(PythonSerializer):
    json_kwargs: dict[str, Any]

def Deserializer(
    stream_or_string: IO[bytes] | IO[str] | bytes | str, **options: Any
) -> Iterator[DeserializedObject]: ...

class DjangoJSONEncoder(json.JSONEncoder):
    allow_nan: bool
    check_circular: bool
    ensure_ascii: bool
    indent: int
    skipkeys: bool
    sort_keys: bool
