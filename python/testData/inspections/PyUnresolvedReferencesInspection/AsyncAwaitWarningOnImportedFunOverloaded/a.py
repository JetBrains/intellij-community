from b import overloaded_fun_async_with_implicit_return_type


async def expect_no_warning():
    await overloaded_fun_async_with_implicit_return_type(1)
