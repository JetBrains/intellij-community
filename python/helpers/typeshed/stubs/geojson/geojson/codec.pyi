import json
from _typeshed import SupportsRead, SupportsWrite
from collections.abc import Callable
from typing import Any
from typing_extensions import Never

from geojson.base import GeoJSON

class GeoJSONEncoder(json.JSONEncoder):
    def default(self, obj) -> GeoJSON: ...

def dump(
    obj, fp: SupportsWrite[str], cls: type[json.JSONEncoder] | None = json.JSONEncoder, allow_nan: bool = False, **kwargs
) -> None: ...
def dumps(
    obj, cls: type[json.JSONEncoder] | None = json.JSONEncoder, allow_nan: bool = False, ensure_ascii: bool = False, **kwargs
) -> str: ...
def load(
    fp: SupportsRead[str],
    cls: type[json.JSONDecoder] = json.JSONDecoder,
    parse_constant: Callable[..., Never] = ...,
    object_hook: Callable[[dict[str, Any]], GeoJSON] = GeoJSON.to_instance,
    **kwargs,
) -> GeoJSON: ...
def loads(
    s: str,
    cls: type[json.JSONDecoder] = json.JSONDecoder,
    parse_constant: Callable[..., Never] = ...,
    object_hook: Callable[[dict[str, Any]], GeoJSON] = GeoJSON.to_instance,
    **kwargs,
) -> GeoJSON: ...

PyGFPEncoder = GeoJSONEncoder
