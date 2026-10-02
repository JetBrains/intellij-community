from __future__ import annotations

import ctypes
from typing import Type
from typing_extensions import assert_type

assert_type(ctypes.POINTER(None), Type[ctypes.c_void_p])
assert_type(ctypes.POINTER(ctypes.c_int), Type[ctypes._Pointer[ctypes.c_int]])
