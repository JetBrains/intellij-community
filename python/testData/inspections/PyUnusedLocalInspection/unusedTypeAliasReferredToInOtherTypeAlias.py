from typing import Any


def f():
    type JsonObject = dict[str, Any]
    type JsonLines = list[JsonObject]
    type <weak_warning descr="Local type alias 'JsonArray' is not used">JsonArray</weak_warning> = list[Any]

    xs: JsonLines = ...
    return xs
