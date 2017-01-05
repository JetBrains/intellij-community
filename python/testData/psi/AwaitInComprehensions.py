async def async2():
    [await fun() for fun in funcs]
    {await fun() for fun in funcs}
    {fun: await fun() for fun in funcs}

    [await fun() for fun in funcs if await smth]
    {await fun() for fun in funcs if await smth}
    {fun: await fun() for fun in funcs if await smth}

    [await fun() async for fun in funcs]
    {await fun() async for fun in funcs}
    {fun: await fun() async for fun in funcs}

    [await fun() async for fun in funcs if await smth]
    {await fun() async for fun in funcs if await smth}
    {fun: await fun() async for fun in funcs if await smth}