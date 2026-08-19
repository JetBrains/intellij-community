import datetime
from _typeshed import Incomplete
from collections.abc import Set as AbstractSet
from logging import Logger

from dateparser.conf import Settings

logger: Logger

class _NgramDateSearch:
    max_tokens: int
    def __init__(self, max_tokens: int = 7) -> None: ...
    def search_parse(
        self,
        languages: list[str] | tuple[str, ...] | AbstractSet[str] | None,
        text: str,
        settings: Settings | dict[str, Incomplete] | None,
    ) -> list[tuple[str, datetime.datetime]]: ...
