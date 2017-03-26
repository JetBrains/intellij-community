"""Stub file for the '_json' module."""

from typing import Any, Tuple

class make_encoder:
    sort_keys = ...  # type: Any
    skipkeys = ...  # type: Any
    key_separator = ...  # type: Any
    indent = ...  # type: Any
    markers = ...  # type: Any
    default = ...  # type: Any
    encoder = ...  # type: Any
    item_separator = ...  # type: Any
    def __init__(self, markers, default, encoder, indent, key_separator,
                 item_separator, sort_keys, skipkeys, allow_nan) -> None: ...
    def __call__(self, *args, **kwargs) -> Any: ...

class make_scanner:
    object_hook = ...  # type: Any
    object_pairs_hook = ...  # type: Any
    parse_int = ...  # type: Any
    parse_constant = ...  # type: Any
    parse_float = ...  # type: Any
    strict = ...  # type: bool
    # TODO: 'context' needs the attrs above (ducktype), but not __call__.
    def __init__(self, context: "make_scanner") -> None: ...
    def __call__(self, string: str, index: int) -> Tuple[Any, int]: ...

def encode_basestring_ascii(s: str) -> str: ...
def scanstring(string: str, end: int, strict: bool = ...) -> Tuple[str, int]: ...
