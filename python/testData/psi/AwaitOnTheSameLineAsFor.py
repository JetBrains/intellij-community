async def y(a1, a2):
    for i in range(10): a1.foo(await a1.x(a2))