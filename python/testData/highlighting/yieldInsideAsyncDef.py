async def foo(x):
    await x
    <error descr="'yield' inside async function">yield x</error>
    <error descr="'yield' inside async function">yield from x</error>
    return x
