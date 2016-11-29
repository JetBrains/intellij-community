async def foo(x):
    await x
    yield x
    <error descr="non-empty 'return' inside asynchronous generator">return x</error>


async def bar(x):
    await x
    yield x
    return
