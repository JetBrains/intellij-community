from typing import Any, Coroutine


async def bar() -> int:
    return 42

def foo(x, y) -> Coroutine[Any, Any, int]:
    return bar()