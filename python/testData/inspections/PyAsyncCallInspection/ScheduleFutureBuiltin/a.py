import asyncio


async def nested():
    print("42")


async def main():
    # Schedule nested() to run soon concurrently with "main()".
    asyncio.ensure_future(nested())  # new in Python 3.4


async def foo():
    loop = asyncio.get_running_loop()
    loop.create_task(nested())  # new in Python 3.4

    loop.run_in_executor(None, nested) # new in Python 3.4


