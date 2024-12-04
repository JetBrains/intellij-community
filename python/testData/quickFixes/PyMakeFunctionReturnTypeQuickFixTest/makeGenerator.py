async def gen() -> str:
    b: bool = <warning descr="Expected type 'str', got 'AsyncGenerator[str | float, Any]' instead"><caret>yield "str"</warning>
    if b:
        b = <warning descr="Expected type 'str', got 'AsyncGenerator[str | float, Any]' instead">yield 3.14</warning>