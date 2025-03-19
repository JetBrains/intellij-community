class A:
    def __init__(self):
        self.x = 1

async def f():
    a = A()
    await a.<caret><warning descr="Unresolved attribute reference 'y' for class 'A'">y</warning>()
