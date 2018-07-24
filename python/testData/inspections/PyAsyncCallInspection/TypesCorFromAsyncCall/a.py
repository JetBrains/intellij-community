import types


def foo():
    return 23


@types.coroutine
def bar():
    yield from foo()


async def check():
    <warning descr="Coroutine 'bar' is not awaited">bar()</warning>