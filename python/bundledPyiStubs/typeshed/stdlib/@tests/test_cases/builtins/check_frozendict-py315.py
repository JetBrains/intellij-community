from __future__ import annotations

import sys
from typing import Any
from typing_extensions import assert_type

if sys.version_info >= (3, 15):
    # Regression test for gh-15985: ``frozendict`` can be constructed from
    # keyword arguments, a mapping, or an iterable of pairs (not just with no
    # arguments).
    assert_type(frozendict(), frozendict[Any, Any])
    assert_type(frozendict(a=1), frozendict[str, int])
    assert_type(frozendict({"x": 1}), frozendict[str, int])
    assert_type(frozendict([("k", 2)]), frozendict[str, int])
    assert_type(frozendict({"x": 1}, y=2), frozendict[str, int])
