from collections.abc import Iterable
from typing import Any

__ALL__: list[str]
VERSION: tuple[int, ...]
ATTRIBUTES: dict[str, int]
COLORS: dict[str, int]
HIGHLIGHTS: dict[str, int]
RESET: str

def colored(text: str, color: str | None = ..., on_color: str | None = ..., attrs: Iterable[str] | None = ...) -> str: ...
def cprint(
    text: str, color: str | None = ..., on_color: str | None = ..., attrs: Iterable[str] | None = ..., **kwargs: Any
) -> None: ...
