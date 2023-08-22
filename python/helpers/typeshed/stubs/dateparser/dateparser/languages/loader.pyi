from collections import OrderedDict
from collections.abc import Iterator
from typing import Any

from .locale import Locale

LOCALE_SPLIT_PATTERN: Any

class LocaleDataLoader:
    def get_locale_map(
        self,
        languages: list[str] | None = ...,
        locales: list[str] | None = ...,
        region: str | None = ...,
        use_given_order: bool = ...,
        allow_conflicting_locales: bool = ...,
    ) -> OrderedDict[str, list[Any] | str | int]: ...
    def get_locales(
        self,
        languages: list[str] | None = ...,
        locales: list[str] | None = ...,
        region: str | None = ...,
        use_given_order: bool = ...,
        allow_conflicting_locales: bool = ...,
    ) -> Iterator[Locale]: ...
    def get_locale(self, shortname: str) -> Locale: ...

default_loader: Any
