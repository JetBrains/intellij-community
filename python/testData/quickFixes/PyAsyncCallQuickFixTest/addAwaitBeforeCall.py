

async def bar():
    return "hey"


async def foo():
    <warning descr="Coroutine 'bar' is not awaited"><caret>bar()</warning>
    return True
