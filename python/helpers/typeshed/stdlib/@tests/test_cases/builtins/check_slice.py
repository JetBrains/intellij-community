"""
Assuming X, Y and Z are types other than None, the following rules apply to the slice type:

- The type hint `slice` should be compatible with all slices, including:
    - `slice(None)`, `slice(None, None)` and `slice(None, None, None)`. (⟿ `slice[?, ?, ?]`)
- The type hint `slice[T]` should be compatible with:
    - `slice(None)`, `slice(None, None)` and `slice(None, None, None)` (⟿ `slice[?, ?, ?]`)
    - `slice(t)`, `slice(None, t)` and `slice(None, t, None)`.  (⟿ `slice[?, T, ?]`)
    - `slice(t, None)` and `slice(t, None, None)`.  (⟿ `slice[T, ?, ?]`)
    - `slice(t, t)` and `slice(t, t, None)`.  (⟿ `slice[T, T, ?]`)
- The type hint `slice[X, Y]` should be compatible with:
    - `slice(None)`, `slice(None, None)` and `slice(None, None, None)` (⟿ `slice[?, ?, ?]`)
    - `slice(y)`, `slice(None, y)` and `slice(None, y, None)`.  (⟿ `slice[?, Y, ?]`)
    - `slice(x, None)` and `slice(x, None, None)` (⟿ `slice[X, ?, ?]`)
    - `slice(x, y)` and `slice(x, y, None)`.  (⟿ `slice[X, Y, ?]`)
-  The type hint `slice[X, Y, Z]` should be compatible with:
    - `slice(None)`, `slice(None, None)` and `slice(None, None, None)`. (⟿ `slice[?, ?, ?]`)
    - `slice(y)`, `slice(None, y)` and `slice(None, y, None)`.   (⟿ `slice[?, Y, ?]`)
    - `slice(x, None)` and `slice(x, None, None)` (⟿ `slice[X, ?, ?]`)
    - `slice(x, y)` and `slice(x, y, None)`.  (⟿ `slice[X, Y, ?]`)
    - `slice(None, None, z)`  (⟿ `slice[?, ?, Z]`)
    - `slice(None, y, z)`   (⟿ `slice[?, Y, Z]`)
    - `slice(x, None, z)`   (⟿ `slice[X, ?, Z]`)
    - `slice(x, y, z)`  (⟿ `slice[X, Y, Z]`)

Consistency criterion: Assuming now X, Y, Z can potentially be None, the following rules apply:

- `slice(x)` must be compatible with `slice[None, X, None]`, even if X is None.
- `slice(x, y)` must be compatible with `slice[X,Y,None]`, even if X is None or Y is None.
- `slice(x, y, z)` must be compatible with `slice[X, Y, Z]`, even if X, Y, or Z are `None`.
"""

from __future__ import annotations

from datetime import date, datetime as DT, timedelta as TD
from typing import Any, SupportsIndex, cast
from typing_extensions import assert_type

# region Tests for slice constructor overloads -----------------------------------------
assert_type(slice(None), "slice[Any, Any, Any]")
assert_type(slice(1234), "slice[Any, int, Any]")

assert_type(slice(None, None), "slice[Any, Any, Any]")
assert_type(slice(None, 5678), "slice[Any, int, Any]")
assert_type(slice(1234, None), "slice[int, Any, Any]")
assert_type(slice(1234, 5678), "slice[int, int, Any]")

assert_type(slice(None, None, None), "slice[Any, Any, Any]")
assert_type(slice(None, 5678, None), "slice[Any, int, Any]")
assert_type(slice(1234, None, None), "slice[int, Any, Any]")
assert_type(slice(1234, 5678, None), "slice[int, int, Any]")
assert_type(slice(1234, 5678, 9012), "slice[int, int, int]")
# endregion Tests for slice constructor overloads --------------------------------------

# region Test parameter defaults for slice constructor ---------------------------------
# Note: need to cast, because pyright specializes regardless of type annotations
slc1 = cast("slice[SupportsIndex | None]", slice(1))
slc2 = cast("slice[int | None, int | None]", slice(1, 2))
fake_key_val = cast("slice[str, int]", slice("1", 2))
assert_type(slc1, "slice[SupportsIndex | None, SupportsIndex | None, SupportsIndex | None]")
assert_type(slc2, "slice[int | None, int | None, int | None]")
assert_type(fake_key_val, "slice[str, int, str | int]")
# endregion Test parameter defaults for slice constructor ------------------------------

# region Tests for slice properties ----------------------------------------------------
# Note: if an argument is not None, we should get precisely the same type back
assert_type(slice(1234).stop, int)

assert_type(slice(1234, None).start, int)
assert_type(slice(None, 5678).stop, int)

assert_type(slice(1234, None, None).start, int)
assert_type(slice(None, 5678, None).stop, int)
assert_type(slice(None, None, 9012).step, int)
# endregion Tests for slice properties -------------------------------------------------


# region Test for slice assignments ----------------------------------------------------
# exhaustively test all possible assignments: miss (X), None (N), int (I), and str (S)
rXNX: slice = slice(None)
rXIX: slice = slice(1234)
rXSX: slice = slice("70")

rNNX: slice = slice(None, None)
rINX: slice = slice(1234, None)
rSNX: slice = slice("70", None)

rNIX: slice = slice(None, 5678)
rIIX: slice = slice(1234, 5678)
rSIX: slice = slice("70", 9012)

rNSX: slice = slice(None, "71")
rISX: slice = slice(1234, "71")
rSSX: slice = slice("70", "71")

rNNN: slice = slice(None, None, None)
rINN: slice = slice(1234, None, None)
rSNN: slice = slice("70", None, None)
rNIN: slice = slice(None, 5678, None)
rIIN: slice = slice(1234, 5678, None)
rSIN: slice = slice("70", 5678, None)
rNSN: slice = slice(None, "71", None)
rISN: slice = slice(1234, "71", None)
rSSN: slice = slice("70", "71", None)

rNNI: slice = slice(None, None, 9012)
rINI: slice = slice(1234, None, 9012)
rSNI: slice = slice("70", None, 9012)
rNII: slice = slice(None, 5678, 9012)
rIII: slice = slice(1234, 5678, 9012)
rSII: slice = slice("70", 5678, 9012)
rNSI: slice = slice(None, "71", 9012)
rISI: slice = slice(1234, "71", 9012)
rSSI: slice = slice("70", "71", 9012)

rNNS: slice = slice(None, None, "1d")
rINS: slice = slice(1234, None, "1d")
rSNS: slice = slice("70", None, "1d")
rNIS: slice = slice(None, 5678, "1d")
rIIS: slice = slice(1234, 5678, "1d")
rSIS: slice = slice("70", 5678, "1d")
rNSS: slice = slice(None, "71", "1d")
rISS: slice = slice(1234, "71", "1d")
rSSS: slice = slice("70", "71", "1d")
# endregion Test for slice assignments -------------------------------------------------


# region Tests for slice[T] assignments ------------------------------------------------
sXNX: "slice[int]" = slice(None)
sXIX: "slice[int]" = slice(1234)

sNNX: "slice[int]" = slice(None, None)
sNIX: "slice[int]" = slice(None, 5678)
sINX: "slice[int]" = slice(1234, None)
sIIX: "slice[int]" = slice(1234, 5678)

sNNN: "slice[int]" = slice(None, None, None)
sNIN: "slice[int]" = slice(None, 5678, None)
sNNS: "slice[int]" = slice(None, None, 9012)
sINN: "slice[int]" = slice(1234, None, None)
sINS: "slice[int]" = slice(1234, None, 9012)
sIIN: "slice[int]" = slice(1234, 5678, None)
sIIS: "slice[int]" = slice(1234, 5678, 9012)
# endregion Tests for slice[T] assignments ---------------------------------------------


# region Tests for slice[X, Y] assignments ---------------------------------------------
# Note: start=int is illegal and hence we add an explicit "type: ignore" comment.
tXNX: "slice[None, int]" = slice(None)  # since slice(None) is slice[Any, Any, Any]
tXIX: "slice[None, int]" = slice(1234)

tNNX: "slice[None, int]" = slice(None, None)
tNIX: "slice[None, int]" = slice(None, 5678)
tINX: "slice[None, int]" = slice(1234, None)  # type: ignore
tIIX: "slice[None, int]" = slice(1234, 5678)  # type: ignore

tNNN: "slice[None, int]" = slice(None, None, None)
tNIN: "slice[None, int]" = slice(None, 5678, None)
tINN: "slice[None, int]" = slice(1234, None, None)  # type: ignore
tIIN: "slice[None, int]" = slice(1234, 5678, None)  # type: ignore
tNNS: "slice[None, int]" = slice(None, None, 9012)
tINS: "slice[None, int]" = slice(None, 5678, 9012)
tNIS: "slice[None, int]" = slice(1234, None, 9012)  # type: ignore
tIIS: "slice[None, int]" = slice(1234, 5678, 9012)  # type: ignore
# endregion Tests for slice[X, Y] assignments ------------------------------------------


# region Tests for slice[X, Y, Z] assignments ------------------------------------------
uXNX: "slice[int, int, int]" = slice(None)
uXIX: "slice[int, int, int]" = slice(1234)

uNNX: "slice[int, int, int]" = slice(None, None)
uNIX: "slice[int, int, int]" = slice(None, 5678)
uINX: "slice[int, int, int]" = slice(1234, None)
uIIX: "slice[int, int, int]" = slice(1234, 5678)

uNNN: "slice[int, int, int]" = slice(None, None, None)
uNNI: "slice[int, int, int]" = slice(None, None, 9012)
uNIN: "slice[int, int, int]" = slice(None, 5678, None)
uNII: "slice[int, int, int]" = slice(None, 5678, 9012)
uINN: "slice[int, int, int]" = slice(1234, None, None)
uINI: "slice[int, int, int]" = slice(1234, None, 9012)
uIIN: "slice[int, int, int]" = slice(1234, 5678, None)
uIII: "slice[int, int, int]" = slice(1234, 5678, 9012)
# endregion Tests for slice[X, Y, Z] assignments ---------------------------------------


# region Test for slice consistency criterion ------------------------------------------
year = date(2021, 1, 1)
vXNX: "slice[None, None, None]" = slice(None)
vXIX: "slice[None, date, None]" = slice(year)

vNNX: "slice[None, None, None]" = slice(None, None)
vNIX: "slice[None, date, None]" = slice(None, year)
vINX: "slice[date, None, None]" = slice(year, None)
vIIX: "slice[date, date, None]" = slice(year, year)

vNNN: "slice[None, None, None]" = slice(None, None, None)
vNIN: "slice[None, date, None]" = slice(None, year, None)
vINN: "slice[date, None, None]" = slice(year, None, None)
vIIN: "slice[date, date, None]" = slice(year, year, None)
vNNI: "slice[None, None, str]" = slice(None, None, "1d")
vNII: "slice[None, date, str]" = slice(None, year, "1d")
vINI: "slice[date, None, str]" = slice(year, None, "1d")
vIII: "slice[date, date, str]" = slice(year, year, "1d")
# endregion Test for slice consistency criterion ---------------------------------------


# region Integration tests for slices with datetimes -----------------------------------
class TimeSeries:  # similar to pandas.Series with datetime index
    def __getitem__(self, key: "slice[DT | str | None, DT | str | None]") -> Any:
        """Subsample the time series at the given dates."""
        ...


class TimeSeriesInterpolator:  # similar to pandas.Series with datetime index
    def __getitem__(self, key: "slice[DT, DT, TD | None]") -> Any:
        """Subsample the time series at the given dates."""
        ...


# tests slices as an argument
start = DT(1970, 1, 1)
stop = DT(1971, 1, 10)
step = TD(days=1)
# see: https://pandas.pydata.org/docs/user_guide/timeseries.html#partial-string-indexing
# FIXME: https://github.com/python/mypy/issues/2410 (use literal slices)
series = TimeSeries()
_ = series[slice(None, "1970-01-10")]
_ = series[slice("1970-01-01", None)]
_ = series[slice("1970-01-01", "1971-01-10")]
_ = series[slice(None, stop)]
_ = series[slice(start, None)]
_ = series[slice(start, stop)]
_ = series[slice(None)]

model = TimeSeriesInterpolator()
_ = model[slice(start, stop)]
_ = model[slice(start, stop, step)]
_ = model[slice(start, stop, None)]


# test slices as a return type
def foo(flag: bool, value: DT) -> "slice[DT, None] | slice[None, DT]":
    if flag:
        return slice(value, None)  # slice[DT, DT|Any, Any] incompatible
    else:
        return slice(None, value)  # slice[DT|Any, DT, Any] incompatible


# endregion Integration tests for slices with datetimes --------------------------------
