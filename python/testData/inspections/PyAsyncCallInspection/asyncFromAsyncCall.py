

async def bar():
    return "hey"


async def foo():
    <warning descr="Coroutine 'bar' is not awaited">bar()</warning>
    return True
