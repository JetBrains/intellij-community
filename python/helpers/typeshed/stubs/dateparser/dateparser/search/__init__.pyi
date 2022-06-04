import sys
from collections.abc import Mapping, Set as AbstractSet
from datetime import datetime
from typing import Any, overload

if sys.version_info >= (3, 8):
    from typing import Literal
else:
    from typing_extensions import Literal

@overload
def search_dates(
    text: str,
    languages: list[str] | tuple[str, ...] | AbstractSet[str] | None,
    settings: Mapping[Any, Any] | None,
    add_detected_language: Literal[True],
) -> list[tuple[str, datetime, str]]: ...
@overload
def search_dates(
    text: str,
    languages: list[str] | tuple[str, ...] | AbstractSet[str] | None = ...,
    settings: Mapping[Any, Any] | None = ...,
    add_detected_language: Literal[False] = ...,
) -> list[tuple[str, datetime]]: ...
