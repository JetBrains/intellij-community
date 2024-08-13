from collections.abc import Callable, Sequence
from typing import Any, Literal

color_names: tuple[str, ...]
foreground: dict[str, str]
background: dict[str, str]
RESET: Literal["0"]
opt_dict: dict[str, str]

def colorize(text: str | None = ..., opts: Sequence[str] = ..., *, fg: str = ..., bg: str = ...) -> str: ...
def make_style(opts: Sequence[str] = ..., *, fg: str = ..., bg: str = ...) -> Callable[[str | None], str]: ...

NOCOLOR_PALETTE: str
DARK_PALETTE: str
LIGHT_PALETTE: str
PALETTES: Any
DEFAULT_PALETTE: str

def parse_color_setting(config_string: str) -> dict[str, dict[str, tuple[str, ...] | str]] | None: ...
