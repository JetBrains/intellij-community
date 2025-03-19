class A:
    def __init__(self):
        self.x = 1

    async def y(self):
        pass


async def f():
    a = A()
    await a.y()
