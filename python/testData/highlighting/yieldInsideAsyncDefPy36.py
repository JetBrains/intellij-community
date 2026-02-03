async def foo(x):
    await x
    yield x
    <error descr="Python does not support 'yield from' inside async functions">yield from x</error>
    <error descr="non-empty 'return' inside asynchronous generator">return x</error>
