import asyncio

def useless_decorator(param):
    def decorator(func):
        pass
    return decorator

@useless_decorator(param=(<error descr="'await' outside async function">await</error> asyncio.sleep(1)))
def fun():
    pass

@useless_decorator(param=(<error descr="'await' outside async function">await</error> asyncio.sleep(1)))
async def fun2():
    pass