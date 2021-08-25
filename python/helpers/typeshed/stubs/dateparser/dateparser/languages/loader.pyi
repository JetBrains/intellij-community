from collections import OrderedDict
from typing import Any, Iterator, List, Optional, Union

from .locale import Locale

LOCALE_SPLIT_PATTERN: Any

class LocaleDataLoader:
    def get_locale_map(
        self,
        languages: Optional[List[str]] = ...,
        locales: Optional[List[str]] = ...,
        region: str | None = ...,
        use_given_order: bool = ...,
        allow_conflicting_locales: bool = ...,
    ) -> OrderedDict[str, Union[List[Any], str, int]]: ...
    def get_locales(
        self,
        languages: Optional[List[str]] = ...,
        locales: Optional[List[str]] = ...,
        region: str | None = ...,
        use_given_order: bool = ...,
        allow_conflicting_locales: bool = ...,
    ) -> Iterator[Locale]: ...
    def get_locale(self, shortname: str) -> Locale: ...

default_loader: Any
