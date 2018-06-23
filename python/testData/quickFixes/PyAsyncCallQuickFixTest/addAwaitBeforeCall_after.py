

async def bar():
    return "hey"


async def foo():
    await bar()
    return True
