class A:
    async def foo(self):
        pass

class B(A):
    async def foo(self):
        return await super().foo()

