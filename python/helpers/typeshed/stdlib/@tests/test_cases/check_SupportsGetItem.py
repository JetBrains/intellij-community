from __future__ import annotations

from _typeshed import SupportsDunderGT, SupportsDunderLT, SupportsGetItem
from collections.abc import Callable
from operator import itemgetter
from typing import Any, TypeVar
from typing_extensions import assert_type

_T = TypeVar("_T")


# This should be equivalent to itemgetter().__call__
def standalone_call(obj: SupportsGetItem[Any, _T]) -> _T: ...


# Expected type of itemgetter(1).__call__
expected_type_itemgetter_call: Callable[[SupportsGetItem[int, _T]], _T]  # pyright: ignore[reportGeneralTypeIssues]

# Expecting itemgetter(1) to be assignable to this
# based on the example below: min({"first": 1, "second": 2}.items(), key=itemgetter(1))
# That example and assigning to this variable are what failed in https://github.com/python/mypy/issues/14032
expected_assignable_to: Callable[[tuple[str, int]], SupportsDunderLT[Any] | SupportsDunderGT[Any]]


# Regression tests for https://github.com/python/mypy/issues/14032
# assert_type(itemgetter("first")({"first": 1, "second": 2}), int) # See comment on itemgetter.__call__
assert_type(min({"first": 1, "second": 2}, key=itemgetter(1)), str)
assert_type(min({"first": 1, "second": 2}.items(), key=itemgetter(1)), tuple[str, int])
assert_type(standalone_call({"first": 1, "second": 2}), int)
assert_type(min({"first": 1, "second": 2}, key=standalone_call), str)
assert_type(min({"first": 1, "second": 2}.items(), key=standalone_call), tuple[str, int])

expected_itemgetter_call_type = itemgetter(1).__call__
expected_itemgetter_call_type = itemgetter(1)
expected_assignable_to = itemgetter(1)

expected_itemgetter_call_type = standalone_call
expected_assignable_to = standalone_call
