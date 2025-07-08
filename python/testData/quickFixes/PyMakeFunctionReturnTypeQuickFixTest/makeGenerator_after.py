from typing import AsyncGenerator, Any


async def gen() -> AsyncGenerator[str | float, Any]:
    b: bool = <caret>yield "str"
    if b:
        b = yield 3.14