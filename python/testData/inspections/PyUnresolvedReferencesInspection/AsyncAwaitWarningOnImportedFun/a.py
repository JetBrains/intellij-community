from b import fun_async, fun_non_async


async def expect_no_warning():
    await fun_async()


async def expect_new_warning():
    await <weak_warning descr="Function 'fun_non_async()' neither declared as 'async' nor with 'Awaitable' as return type">fun_non_async()</weak_warning>


def local_fun_non_async():
    pass


async def expect_warning():
    <warning descr="Class 'None' does not define '__await__', so the 'await' operator cannot be used on its instances">await</warning> local_fun_non_async()
