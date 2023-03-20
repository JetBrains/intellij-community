from _typeshed import Incomplete
from collections.abc import Callable
from typing import ClassVar

from .prettytable import PrettyTable

RESET_CODE: str

init: Callable[[], object]

class Theme:
    default_color: str
    vertical_char: str
    vertical_color: str
    horizontal_char: str
    horizontal_color: str
    junction_char: str
    junction_color: str
    def __init__(
        self,
        default_color: str = ...,
        vertical_char: str = ...,
        vertical_color: str = ...,
        horizontal_char: str = ...,
        horizontal_color: str = ...,
        junction_char: str = ...,
        junction_color: str = ...,
    ) -> None: ...
    # The following method is broken in upstream code.
    def format_code(s: str) -> str: ...  # type: ignore[misc]

class Themes:
    DEFAULT: ClassVar[Theme]
    OCEAN: ClassVar[Theme]

class ColorTable(PrettyTable):
    def __init__(self, field_names: Incomplete | None = ..., **kwargs) -> None: ...
    @property
    def theme(self) -> Theme: ...
    @theme.setter
    def theme(self, value: Theme): ...
    def update_theme(self) -> None: ...
    def get_string(self, **kwargs): ...
