from types import CoroutineType
from typing import Any


async def bar() -> int:
    return 42

def foo(x, y) -> CoroutineType[Any, Any, int]:
    return bar()