from __future__ import annotations

from typing import Any, KeysView, TypeVar

KT = TypeVar("KT")


class MyKeysView(KeysView[KT]):
    pass


d: dict[Any, Any] = {}
dict_keys = type(d.keys())

# This should not cause an error like `Member "register" is unknown`:
MyKeysView.register(dict_keys)
