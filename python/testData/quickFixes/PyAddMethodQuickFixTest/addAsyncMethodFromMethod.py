class A:
    def __init__(self):
        self.x = 1

    async def foo(self, a):
        await self.<caret><warning descr="Unresolved attribute reference 'y' for class 'A'">y</warning>(1, a)

# Some comment

class B:
    pass
