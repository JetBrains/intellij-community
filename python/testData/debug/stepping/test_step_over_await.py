import asyncio


async def compute(x, y):
    print("Compute %s + %s ..." % (x, y))
    await asyncio.sleep(1.0)
    return x + y


async def print_sum(x, y):
    result = await compute(x, y)  # breakpoint and Step Over
    print("%s + %s = %s" % (x, y, result))


z = 42
loop = asyncio.get_event_loop()
loop.run_until_complete(print_sum(1, 2))
loop.close()
