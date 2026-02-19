async def f() -> bool:
    return True


async def main():
    if <warning descr="Coroutine not awaited in boolean context"><caret>f()</warning>:
        print("hi")
