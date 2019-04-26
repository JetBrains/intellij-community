import asyncio


async def nested():
    print("42")


async def main():
    # Schedule nested() to run soon concurrently with "main()".
    asyncio.create_task(nested())
    await asyncio.sleep(3)


asyncio.run(main())
