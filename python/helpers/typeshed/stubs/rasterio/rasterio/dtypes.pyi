from collections.abc import Sequence
from typing import Any, Final

import numpy as np
from numpy.typing import ArrayLike, DTypeLike

bool_: Final[str]
ubyte: Final[str]
uint8: Final[str]
sbyte: Final[str]
int8: Final[str]
uint16: Final[str]
int16: Final[str]
uint32: Final[str]
int32: Final[str]
int64: Final[str]
uint64: Final[str]
float16: Final[str]
float32: Final[str]
float64: Final[str]
complex_: Final[str]
complex64: Final[str]
complex128: Final[str]
complex_int16: Final[str]

dtype_fwd: Final[dict[int, str | None]]
dtype_rev: Final[dict[str | None, int]]
typename_fwd: Final[dict[int, str]]
typename_rev: Final[dict[str, int]]
dtype_ranges: Final[dict[str, tuple[float, float]]]
dtype_info_registry: Final[dict[str, type]]

# `numpy.finfo` instances cached at module import; used by `in_dtype_range`.
f16i: Final[np.finfo[np.float16]]
f32i: Final[np.finfo[np.float32]]
f64i: Final[np.finfo[np.float64]]

def in_dtype_range(value: float, dtype: DTypeLike) -> bool: ...
def check_dtype(dt: DTypeLike) -> bool: ...
def get_minimum_dtype(values: ArrayLike) -> str: ...

# isinstance check; accepts any object and returns True for numpy.ndarray
# and any object exposing `__array__`.
def is_ndarray(array: Any) -> bool: ...
def can_cast_dtype(values: ArrayLike, dtype: DTypeLike) -> bool: ...
def validate_dtype(values: ArrayLike, valid_dtypes: Sequence[DTypeLike]) -> bool: ...
