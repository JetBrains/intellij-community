import json
from typing import Any, TypeAlias

import _typeshed
from django.core.serializers.python import Deserializer as PythonDeserializer
from django.core.serializers.python import Serializer as PythonSerializer

_DeserializerInput: TypeAlias = _typeshed.SupportsRead[bytes | str] | bytes | str

class Serializer(PythonSerializer):
    json_kwargs: dict[str, Any]

class Deserializer(PythonDeserializer):
    def __init__(self, stream_or_string: _DeserializerInput, **options: Any) -> None: ...

class DjangoJSONEncoder(json.JSONEncoder):
    allow_nan: bool
    check_circular: bool
    ensure_ascii: bool
    indent: int
    skipkeys: bool
    sort_keys: bool
