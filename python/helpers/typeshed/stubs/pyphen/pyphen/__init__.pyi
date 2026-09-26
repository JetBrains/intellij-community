from collections.abc import Generator
from pathlib import Path
from typing import SupportsInt, TypeAlias
from typing_extensions import Self

__all__ = ("LANGUAGES", "Pyphen", "language_fallback")
LANGUAGES: dict[str, Path]
_Data: TypeAlias = tuple[str, int, int]

def language_fallback(language: str) -> str: ...

class AlternativeParser:
    change: str
    index: int
    cut: int

    def __init__(self, pattern: str, alternative: str) -> None: ...
    def __call__(self, value: str | SupportsInt) -> int: ...

class DataInt(int):
    data: _Data | None

    def __new__(cls, value: int, data: _Data | None = ..., reference: DataInt | None = ...) -> Self: ...

class HyphDict:
    patterns: dict[str, tuple[int, tuple[int, ...]]]
    cache: dict[str, list[DataInt]]
    maxlen: int

    def __init__(self, path: Path) -> None: ...
    def positions(self, word: str) -> list[DataInt]: ...

class Pyphen:
    hd: HyphDict

    def __init__(
        self, filename: str | Path | None = ..., lang: str | None = ..., left: int = 2, right: int = 2, cache: bool = True
    ) -> None: ...
    def positions(self, word: str) -> list[DataInt]: ...
    def iterate(self, word: str) -> Generator[tuple[str, str]]: ...
    def wrap(self, word: str, width: int, hyphen: str = "-") -> tuple[str, str] | None: ...
    def inserted(self, word: str, hyphen: str = "-") -> str: ...
    __call__ = iterate
