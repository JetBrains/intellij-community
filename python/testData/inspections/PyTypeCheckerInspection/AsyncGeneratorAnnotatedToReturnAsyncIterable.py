from typing import AsyncIterable


async def g1() -> AsyncIterable[int]:
    yield 42

async def g2() -> AsyncIterable[int]:
    yield 42
    <error descr="non-empty 'return' inside asynchronous generator">return None</error>

async def g3() -> AsyncIterable:
    yield 42

async def g4() -> AsyncIterable:
    yield 42
    <error descr="non-empty 'return' inside asynchronous generator">return None</error>