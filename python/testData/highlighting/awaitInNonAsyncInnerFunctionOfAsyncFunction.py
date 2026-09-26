async def my_function():
    def subfunction(x):
        <error descr="'await' outside async function">await</error> x

async def my_function_correct():
    async def subfunction(x):
        await x

def my_non_async_function_correct():
    async def subfunction(x):
        await x