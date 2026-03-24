from typing import Any

from django.core.serializers.json import Serializer as JSONSerializer
from typing_extensions import override

class Serializer(JSONSerializer):
    @override
    def start_serialization(self) -> None: ...
    @override
    def end_serialization(self) -> None: ...
    geometry_field: Any
    @override
    def start_object(self, obj: Any) -> None: ...
    @override
    def get_dump_object(self, obj: Any) -> Any: ...
    @override
    def handle_field(self, obj: Any, field: Any) -> None: ...

class Deserializer:
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
