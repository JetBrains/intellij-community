from __future__ import annotations

from typing import Sequence, Tuple
from typing_extensions import assert_type

ints: Sequence[int] = [1, 2, 3]
strs: Sequence[str] = ["one", "two", "three"]
floats: Sequence[float] = [1.0, 2.0, 3.0]
str_tuples: Sequence[Tuple[str]] = list((x,) for x in strs)

assert_type(zip(ints), zip[Tuple[int]])
assert_type(zip(ints, strs), zip[Tuple[int, str]])
assert_type(zip(ints, strs, floats), zip[Tuple[int, str, float]])
assert_type(zip(strs, ints, floats, ints), zip[Tuple[str, int, float, int]])
assert_type(zip(strs, ints, floats, ints, str_tuples), zip[Tuple[str, int, float, int, Tuple[str]]])
