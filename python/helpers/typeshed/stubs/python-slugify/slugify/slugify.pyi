from typing import Iterable

def smart_truncate(
    string: str, max_length: int = ..., word_boundary: bool = ..., separator: str = ..., save_order: bool = ...
) -> str: ...
def slugify(
    text: str,
    entities: bool = ...,
    decimal: bool = ...,
    hexadecimal: bool = ...,
    max_length: int = ...,
    word_boundary: bool = ...,
    separator: str = ...,
    save_order: bool = ...,
    stopwords: Iterable[str] = ...,
    regex_pattern: str | None = ...,
    lowercase: bool = ...,
    replacements: Iterable[Iterable[str]] = ...,
) -> str: ...
