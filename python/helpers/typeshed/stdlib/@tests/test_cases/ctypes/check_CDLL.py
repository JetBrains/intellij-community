import ctypes
import sys
from pathlib import Path
from typing_extensions import assert_type

assert_type(ctypes.CDLL(None), ctypes.CDLL)
assert_type(ctypes.CDLL("."), ctypes.CDLL)

# https://github.com/python/cpython/pull/7032
if sys.version_info >= (3, 12):
    assert_type(ctypes.CDLL(Path(".")), ctypes.CDLL)
