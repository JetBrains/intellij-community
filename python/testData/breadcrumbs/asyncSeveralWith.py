async def foo():
    async with open("file.txt"), open("file2.txt"):
        print("a<caret>bc")