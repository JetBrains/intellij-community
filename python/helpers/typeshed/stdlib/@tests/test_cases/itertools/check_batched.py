from __future__ import annotations

import sys
from typing_extensions import assert_type

if sys.version_info >= (3, 13):
    from itertools import batched

    assert_type(batched([0], 1, strict=True), batched[tuple[int]])
    assert_type(batched([0, 0], 2, strict=True), batched[tuple[int, int]])
    assert_type(batched([0, 0, 0], 3, strict=True), batched[tuple[int, int, int]])
    assert_type(batched([0, 0, 0, 0], 4, strict=True), batched[tuple[int, int, int, int]])
    assert_type(batched([0, 0, 0, 0, 0], 5, strict=True), batched[tuple[int, int, int, int, int]])

    assert_type(batched([0], 2), batched[tuple[int, ...]])
    assert_type(batched([0], 2, strict=False), batched[tuple[int, ...]])

    def f() -> int:
        return 3

    assert_type(batched([0, 0, 0], f(), strict=True), batched[tuple[int, ...]])
