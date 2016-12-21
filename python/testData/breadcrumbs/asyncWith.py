async def foo():
    async with open("file.txt"):
        print("a<caret>bc")