def test_async_generator():
    import asyncio
    from typing import TYPE_CHECKING
    if TYPE_CHECKING:
        from typing import AsyncGenerator, Any  # AsyncGenerator is not yet in Python 3.6.0 available at the moment

    async def async_gen():
        # type: () -> AsyncGenerator[int, Any]
        yield 0
        yield 1

    async def f():
        gen = async_gen()
        assert gen.__aiter__() == gen
        assert await gen.__anext__() == 0
        assert gen.ag_await is None
        assert gen.ag_frame is not None
        assert not gen.ag_running
        assert gen.ag_code is not None

        assert await gen.asend(0) == 1
        assert gen.aclose is not None
        assert gen.athrow is not None

    loop = asyncio.get_event_loop()
    loop.run_until_complete(f())
