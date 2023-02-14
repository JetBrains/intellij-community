from collections.abc import Callable
from re import Pattern
from typing_extensions import Literal, TypedDict

_DEFAULT_DELIMITER: str

class _DeprecatedParameter: ...

class _EmojiLisReturn(TypedDict):
    emoji: str
    location: int

class _EmojiListReturn(TypedDict):
    emoji: str
    match_start: int
    match_end: int

def emojize(
    string: str,
    use_aliases: bool | type[_DeprecatedParameter] = ...,
    delimiters: tuple[str, str] = ...,
    variant: Literal["text_type", "emoji_type", None] = ...,
    language: str = ...,
    version: float | None = ...,
    handle_version: str | Callable[[str, dict[str, str]], str] | None = ...,
) -> str: ...
def demojize(
    string: str,
    use_aliases: bool | type[_DeprecatedParameter] = ...,
    delimiters: tuple[str, str] = ...,
    language: str = ...,
    version: float | None = ...,
    handle_version: str | Callable[[str, dict[str, str]], str] | None = ...,
) -> str: ...
def replace_emoji(
    string: str,
    replace: str | Callable[[str, dict[str, str]], str] = ...,
    language: str | type[_DeprecatedParameter] = ...,
    version: float | None = ...,
) -> str: ...
def get_emoji_regexp(language: str | None = ...) -> Pattern[str]: ...
def emoji_lis(string: str, language: str | type[_DeprecatedParameter] = ...) -> list[_EmojiLisReturn]: ...
def emoji_list(string: str) -> list[_EmojiListReturn]: ...
def distinct_emoji_lis(string: str, language: str | type[_DeprecatedParameter] = ...) -> list[str]: ...
def distinct_emoji_list(string: str) -> list[str]: ...
def emoji_count(string: str, unique: bool = ...) -> int: ...
def version(string: str) -> float: ...
def is_emoji(string: str) -> bool: ...
