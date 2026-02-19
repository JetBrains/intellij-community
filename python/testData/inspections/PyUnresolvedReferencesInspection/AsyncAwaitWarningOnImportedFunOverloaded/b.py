from typing import overload


@overload
async def overloaded_fun_async_with_implicit_return_type(arg0: str):
    ...


@overload
def overloaded_fun_async_with_implicit_return_type(arg0: int):
    ...


def overloaded_fun_async_with_implicit_return_type(arg0):
    return 3
