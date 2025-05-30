from contextlib import contextmanager, asynccontextmanager


class CustomContextManager:
    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return


class CustomContextManager2(CustomContextManager):
    pass


class CustomAsyncContextManager:
    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        return


@contextmanager
def generator_cm():
    yield


@asynccontextmanager
async def generator_acm():
    yield


class NonContextManager:
    pass


with (
    CustomContextManager(),
    CustomContextManager2(),
    <warning descr="Expected type 'contextlib.AbstractContextManager', got 'CustomAsyncContextManager' instead">CustomAsyncContextManager()</warning>,
    <warning descr="Expected type 'contextlib.AbstractContextManager', got 'NonContextManager' instead">NonContextManager()</warning>,
    generator_cm(),
    <warning descr="Expected type 'contextlib.AbstractContextManager', got '_AsyncGeneratorContextManager[None]' instead">generator_acm()</warning>,
    <warning descr="Expected type 'contextlib.AbstractContextManager', got 'object' instead">object()</warning>
):
    pass


async def f():
    async with (
        <warning descr="Expected type 'contextlib.AbstractAsyncContextManager', got 'CustomContextManager' instead">CustomContextManager()</warning>,
        <warning descr="Expected type 'contextlib.AbstractAsyncContextManager', got 'CustomContextManager2' instead">CustomContextManager2()</warning>,
        CustomAsyncContextManager(),
        <warning descr="Expected type 'contextlib.AbstractAsyncContextManager', got 'NonContextManager' instead">NonContextManager()</warning>,
        <warning descr="Expected type 'contextlib.AbstractAsyncContextManager', got '_GeneratorContextManager[None]' instead">generator_cm()</warning>,
        generator_acm(),
        <warning descr="Expected type 'contextlib.AbstractAsyncContextManager', got 'object' instead">object()</warning>
    ):
        pass
