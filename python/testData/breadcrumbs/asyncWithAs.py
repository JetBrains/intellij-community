async def foo():
    async with open("file.txt") as f:
        print("a<caret>bc")