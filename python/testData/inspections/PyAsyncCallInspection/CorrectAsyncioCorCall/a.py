import asyncio


@asyncio.coroutine
def foo():
    return 24


@asyncio.coroutine
def baz():
    yield from foo()


@asyncio.coroutine
def gen():
    return foo()


@asyncio.coroutine
def wrap(co):
    res = yield from co
    return res
