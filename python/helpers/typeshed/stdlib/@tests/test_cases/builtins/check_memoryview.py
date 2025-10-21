from __future__ import annotations

import array
from typing_extensions import assert_type

# Casting to bytes.
buf = b"abcdefg"
view = memoryview(buf).cast("c")
elm = view[0]
assert_type(elm, bytes)
assert_type(view[0:2], memoryview[bytes])

# Casting to a bool.
a = array.array("B", [0, 1, 2, 3])
mv = memoryview(a)
bool_mv = mv.cast("?")
assert_type(bool_mv[0], bool)
assert_type(bool_mv[0:2], memoryview[bool])


# Casting to a signed char.
a = array.array("B", [0, 1, 2, 3])
mv = memoryview(a)
signed_mv = mv.cast("b")
assert_type(signed_mv[0], int)
assert_type(signed_mv[0:2], memoryview[int])

# Casting to a signed short.
a = array.array("B", [0, 1, 2, 3])
mv = memoryview(a)
signed_mv = mv.cast("h")
assert_type(signed_mv[0], int)
assert_type(signed_mv[0:2], memoryview[int])

# Casting to a signed int.
a = array.array("B", [0, 1, 2, 3])
mv = memoryview(a)
signed_mv = mv.cast("i")
assert_type(signed_mv[0], int)
assert_type(signed_mv[0:2], memoryview[int])

# Casting to a signed long.
a = array.array("B", [0, 1, 2, 3])
mv = memoryview(a)
signed_mv = mv.cast("l")
assert_type(signed_mv[0], int)
assert_type(signed_mv[0:2], memoryview[int])

# Casting to a float.
a = array.array("B", [0, 1, 2, 3])
mv = memoryview(a)
float_mv = mv.cast("f")
assert_type(float_mv[0], float)
assert_type(float_mv[0:2], memoryview[float])

# An invalid literal should raise an error.
mv = memoryview(b"abc")
mv.cast("abc")  # type: ignore

mv.index(42)  # type: ignore
mv.count(42)  # type: ignore
