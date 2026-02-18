async def f() -> bool:
    return True


async def main():
    if await f() or f():
        print("hi")
