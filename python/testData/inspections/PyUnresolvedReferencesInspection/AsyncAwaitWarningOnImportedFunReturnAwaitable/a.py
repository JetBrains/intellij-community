from b import fun_awaitable_imported, MyAwaitable


async def expect_false_positive_warning():
    await <warning descr="Function 'fun_awaitable_imported()' neither declared as 'async' nor with 'Awaitable' as return type">fun_awaitable_imported()</warning>


async def expect_pass_1():
    await MyAwaitable()


def fun_awaitable_local():
    return MyAwaitable()


async def expect_pass_2():
    await fun_awaitable_local()

