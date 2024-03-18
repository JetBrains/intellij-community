async def f():
    s = 3
    await <error descr="Unresolved reference 'ref'"><caret>ref</error>(s, t=1)