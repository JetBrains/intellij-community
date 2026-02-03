from collections.abc import Callable
from datetime import date, datetime
from typing import Any, SupportsInt

from django import template

register: template.Library

def ordinal(value: str | SupportsInt | None) -> str | None: ...
def intcomma(value: str | SupportsInt | None, use_l10n: bool = True) -> str: ...

intword_converters: tuple[tuple[int, Callable]]

def intword(value: str | SupportsInt | None) -> int | str | None: ...
def apnumber(value: str | SupportsInt | None) -> int | str | None: ...
def naturalday(value: date | str | None, arg: str | None = None) -> str | None: ...
def naturaltime(value: datetime) -> str: ...

class NaturalTimeFormatter:
    time_strings: dict[str, str]
    past_substrings: dict[str, str]
    future_substrings: dict[str, str]
    @classmethod
    def string_for(cls: type[NaturalTimeFormatter], value: Any) -> Any: ...
