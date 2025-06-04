import types
from collections.abc import Iterator
from datetime import date, time
from datetime import datetime as builtin_datetime
from decimal import Decimal
from typing import Any, TypeVar, overload

ISO_INPUT_FORMATS: dict[str, list[str]]
FORMAT_SETTINGS: frozenset[str]

def reset_format_cache() -> None: ...
def iter_format_modules(lang: str, format_module_path: list[str] | str | None = None) -> Iterator[types.ModuleType]: ...
def get_format_modules(lang: str | None = None) -> list[types.ModuleType]: ...
def get_format(format_type: str, lang: str | None = None, use_l10n: bool | None = None) -> Any: ...

get_format_lazy: Any

def date_format(
    value: date | builtin_datetime | str, format: str | None = None, use_l10n: bool | None = None
) -> str: ...
def time_format(
    value: time | builtin_datetime | str, format: str | None = None, use_l10n: bool | None = None
) -> str: ...
def number_format(
    value: Decimal | float | str,
    decimal_pos: int | None = None,
    use_l10n: bool | None = None,
    force_grouping: bool = False,
) -> str: ...

_T = TypeVar("_T")

# Mypy considers this invalid (overlapping signatures), but thanks to implementation
# details it works as expected (all values from Union are `localize`d to str,
# while type of others is preserved)
@overload
def localize(value: builtin_datetime | date | time | Decimal | float | str, use_l10n: bool | None = None) -> str: ...
@overload
def localize(value: _T, use_l10n: bool | None = None) -> _T: ...
@overload
def localize_input(
    value: builtin_datetime | date | time | Decimal | float | str, default: str | None = None
) -> str: ...
@overload
def localize_input(value: _T, default: str | None = None) -> _T: ...
def sanitize_separators(value: _T) -> _T: ...
def sanitize_strftime_format(fmt: str) -> str: ...
