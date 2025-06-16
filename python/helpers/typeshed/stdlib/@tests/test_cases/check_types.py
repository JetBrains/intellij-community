from __future__ import annotations

import sys
import types
from collections import UserDict
from typing import Union
from typing_extensions import assert_type

# test `types.SimpleNamespace`

# Valid:
types.SimpleNamespace()
types.SimpleNamespace(x=1, y=2)

if sys.version_info >= (3, 13):
    types.SimpleNamespace(())
    types.SimpleNamespace([])
    types.SimpleNamespace([("x", "y"), ("z", 1)])
    types.SimpleNamespace({})
    types.SimpleNamespace(UserDict({"x": 1, "y": 2}))


# Invalid:
types.SimpleNamespace(1)  # type: ignore
types.SimpleNamespace([1])  # type: ignore
types.SimpleNamespace([["x"]])  # type: ignore
types.SimpleNamespace(**{1: 2})  # type: ignore
types.SimpleNamespace({1: 2})  # type: ignore
types.SimpleNamespace([[1, 2]])  # type: ignore
types.SimpleNamespace(UserDict({1: 2}))  # type: ignore
types.SimpleNamespace([[[], 2]])  # type: ignore

# test: `types.MappingProxyType`
mp = types.MappingProxyType({1: 2, 3: 4})
mp.get("x")  # type: ignore
item = mp.get(1)
assert_type(item, Union[int, None])
item_2 = mp.get(2, 0)
assert_type(item_2, int)
item_3 = mp.get(3, "default")
assert_type(item_3, Union[int, str])
# Default isn't accepted as a keyword argument.
mp.get(4, default="default")  # type: ignore


# test: `types.DynamicClassAttribute`
class DCAtest:
    _value: int | None = None

    @types.DynamicClassAttribute
    def foo(self) -> int | None:
        return self._value

    @foo.setter
    def foo(self, value: int) -> None:
        self._value = value

    @foo.deleter
    def foo(self) -> None:
        self._value = None
