async def f() -> bool:
    return True


async def main():
    if <warning descr="Coroutine not awaited in boolean context"><caret>f()</warning> or <warning descr="Coroutine not awaited in boolean context">f()</warning>:
        print("hi")
