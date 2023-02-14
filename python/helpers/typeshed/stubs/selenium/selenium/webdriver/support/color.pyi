from typing import Any

RGB_PATTERN: str
RGB_PCT_PATTERN: str
RGBA_PATTERN: str
RGBA_PCT_PATTERN: str
HEX_PATTERN: str
HEX3_PATTERN: str
HSL_PATTERN: str
HSLA_PATTERN: str

class Color:
    match_obj: Any
    @staticmethod
    def from_string(str_): ...
    red: Any
    green: Any
    blue: Any
    alpha: Any
    def __init__(self, red, green, blue, alpha: int = ...) -> None: ...
    @property
    def rgb(self): ...
    @property
    def rgba(self): ...
    @property
    def hex(self): ...
    def __eq__(self, other): ...
    def __ne__(self, other): ...
    def __hash__(self): ...

Colors: Any
