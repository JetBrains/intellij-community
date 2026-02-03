from typing import Any, AsyncGenerator


async def gen() -> AsyncGenerator[str | float, Any]:
    b: bool = <caret>yield "str"
    if b:
        b = yield 3.14