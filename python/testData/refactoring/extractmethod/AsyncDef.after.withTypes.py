from typing import Any


async def foo(x):
    y = await bar(x)
    return await y


async def bar(x_new) -> Any:
    y = await x_new
    return y
