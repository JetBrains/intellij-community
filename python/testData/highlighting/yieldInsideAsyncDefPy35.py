async def foo(x):
    await x
    <error descr="Python version 3.5 does not support 'yield' inside async functions">yield x</error>
    <error descr="Python does not support 'yield from' inside async functions">yield from x</error>
    return x
