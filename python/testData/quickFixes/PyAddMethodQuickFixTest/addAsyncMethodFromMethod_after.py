class A:
    def __init__(self):
        self.x = 1

    async def foo(self, a):
        await self.y(1, a)

    async def y(self, param, a):
        pass


# Some comment

class B:
    pass
