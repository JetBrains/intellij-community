from __future__ import annotations

from typing import Union


def foo() -> int | Union[str, bool]:
    return 42