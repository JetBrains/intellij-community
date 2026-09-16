from collections.abc import Callable
from datetime import datetime, tzinfo
from typing import Literal

from dateparser.conf import Settings

class DateParser:
    def parse(
        self,
        date_string: str,
        parse_method: Callable[
            [str, Settings, tzinfo | None, Literal["DMY", "DYM", "MDY", "MYD", "YDM", "YMD"] | None], tuple[datetime, str]
        ],
        settings: Settings | None = None,
        date_order: Literal["DMY", "DYM", "MDY", "MYD", "YDM", "YMD"] | None = None,
    ) -> tuple[datetime, str]: ...

date_parser: DateParser
