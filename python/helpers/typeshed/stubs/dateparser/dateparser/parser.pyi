import datetime
import re
from collections import OrderedDict
from collections.abc import Callable, Iterable, Iterator
from io import StringIO
from typing import ClassVar, Final, Literal, TypeAlias, overload

from dateparser.conf import Settings

_TokenType: TypeAlias = Literal[0, 1, 2]
_Component: TypeAlias = Literal["year", "month", "day"]

NSP_COMPATIBLE: Final[re.Pattern[str]]
MERIDIAN: Final[re.Pattern[str]]
MICROSECOND: Final[re.Pattern[str]]
EIGHT_DIGIT: Final[re.Pattern[str]]
HOUR_MINUTE_REGEX: Final[re.Pattern[str]]

def no_space_parser_eligibile(datestring: str) -> bool: ...
def get_unresolved_attrs(parser_object: object) -> tuple[list[_Component], list[_Component]]: ...

date_order_chart: Final[dict[str, str]]

@overload
def resolve_date_order(order: str, lst: Literal[True]) -> list[_Component]: ...
@overload
def resolve_date_order(order: str, lst: Literal[False] | None = None) -> str: ...

class _time_parser:
    time_directives: list[str]
    def __call__(self, timestring: str) -> datetime.time: ...

time_parser: _time_parser

class _no_spaces_parser:
    period: dict[str, list[str]]
    date_formats: dict[str, list[str]]
    def __init__(self, *args, **kwargs) -> None: ...
    @classmethod
    def parse(cls, datestring: str, settings: Settings) -> tuple[datetime.datetime, str]: ...

class _parser:
    alpha_directives: ClassVar[dict[str, list[str]]]
    num_directives: ClassVar[dict[str, list[str]]]

    settings: Settings
    tokens: list[tuple[str, _TokenType]]
    filtered_tokens: list[tuple[str, _TokenType, int]]
    unset_tokens: list[tuple[str, _TokenType, _Component]]
    day: int | None
    month: int | None
    year: int | None
    time: Callable[[], datetime.time] | None
    auto_order: list[str]
    ordered_num_directives: OrderedDict[str, list[str]]

    def __init__(self, tokens: Iterable[tuple[str, _TokenType]], settings: Settings) -> None: ...
    @classmethod
    def parse(
        cls, datestring: str, settings: Settings, tz: datetime.tzinfo | None = None
    ) -> tuple[datetime.datetime, str | None]: ...

class tokenizer:
    digits: Literal["0123456789:"]
    letters: Literal["abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"]
    instream: StringIO
    def __init__(self, ds: str) -> None: ...
    def tokenize(self) -> Iterator[tuple[str, _TokenType]]: ...
