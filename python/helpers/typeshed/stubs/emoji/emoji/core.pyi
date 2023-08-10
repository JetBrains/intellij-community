from collections.abc import Callable
from typing_extensions import Literal, TypedDict

_DEFAULT_DELIMITER: str

class _EmojiListReturn(TypedDict):
    emoji: str
    match_start: int
    match_end: int

def emojize(
    string: str,
    delimiters: tuple[str, str] = ...,
    variant: Literal["text_type", "emoji_type", None] = ...,
    language: str = ...,
    version: float | None = ...,
    handle_version: str | Callable[[str, dict[str, str]], str] | None = ...,
) -> str: ...
def demojize(
    string: str,
    delimiters: tuple[str, str] = ...,
    language: str = ...,
    version: float | None = ...,
    handle_version: str | Callable[[str, dict[str, str]], str] | None = ...,
) -> str: ...
def replace_emoji(string: str, replace: str | Callable[[str, dict[str, str]], str] = ..., version: float = ...) -> str: ...
def emoji_list(string: str) -> list[_EmojiListReturn]: ...
def distinct_emoji_list(string: str) -> list[str]: ...
def emoji_count(string: str, unique: bool = ...) -> int: ...
def version(string: str) -> float: ...
def is_emoji(string: str) -> bool: ...
