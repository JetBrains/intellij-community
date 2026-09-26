from typing import AsyncGenerator


async def gen() -> AsyncGenerator[str | float, bool]:
    b: bool = <caret>yield "str"
    if b:
        b = yield 3.14
