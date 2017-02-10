async def foo(x):
    await x
    yield x
    <error descr="Python does not support 'yield from' inside async functions">yield from x</error>
    return x
