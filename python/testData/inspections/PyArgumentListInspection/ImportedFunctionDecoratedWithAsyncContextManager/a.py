from mod import func


async def main():
    async with func(42, "foo"):
        pass