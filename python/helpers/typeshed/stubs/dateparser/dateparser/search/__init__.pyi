from collections.abc import Mapping, Set as AbstractSet
from datetime import datetime
from typing import Any, overload
from typing_extensions import Literal

from ..date import _DetectLanguagesFunction

@overload
def search_dates(
    text: str,
    languages: list[str] | tuple[str, ...] | AbstractSet[str] | None,
    settings: Mapping[Any, Any] | None,
    add_detected_language: Literal[True],
    detect_languages_function: _DetectLanguagesFunction | None = ...,
) -> list[tuple[str, datetime, str]]: ...
@overload
def search_dates(
    text: str,
    languages: list[str] | tuple[str, ...] | AbstractSet[str] | None = ...,
    settings: Mapping[Any, Any] | None = ...,
    add_detected_language: Literal[False] = ...,
    detect_languages_function: _DetectLanguagesFunction | None = ...,
) -> list[tuple[str, datetime]]: ...
