import datetime
from typing_extensions import Literal, TypedDict

from .date import DateDataParser, _DetectLanguagesFunction

__version__: str

_default_parser: DateDataParser

_Part = Literal["day", "month", "year"]
_ParserKind = Literal["timestamp", "relative-time", "custom-formats", "absolute-time", "no-spaces-time"]

class _Settings(TypedDict, total=False):
    DATE_ORDER: str
    PREFER_LOCALE_DATE_ORDER: bool
    TIMEZONE: str
    TO_TIMEZONE: str
    RETURN_AS_TIMEZONE_AWARE: bool
    PREFER_DAY_OF_MONTH: Literal["current", "first", "last"]
    PREFER_DATES_FROM: Literal["current_period", "future", "past"]
    RELATIVE_BASE: datetime.datetime
    STRICT_PARSING: bool
    REQUIRE_PARTS: list[_Part]
    SKIP_TOKENS: list[str]
    NORMALIZE: bool
    RETURN_TIME_AS_PERIOD: bool
    PARSERS: list[_ParserKind]

def parse(
    date_string: str,
    date_formats: list[str] | tuple[str, ...] | set[str] | None = ...,
    languages: list[str] | tuple[str, ...] | set[str] | None = ...,
    locales: list[str] | tuple[str, ...] | set[str] | None = ...,
    region: str | None = ...,
    settings: _Settings | None = ...,
    detect_languages_function: _DetectLanguagesFunction | None = ...,
) -> datetime.datetime | None: ...
