from typing import Any, TypeAlias

_JsonPrimitive: TypeAlias = str | int | float | bool | None
_JsonValue: TypeAlias = _JsonPrimitive | list[Any] | dict[str, Any]

def normalize_json(obj: Any) -> _JsonValue: ...
