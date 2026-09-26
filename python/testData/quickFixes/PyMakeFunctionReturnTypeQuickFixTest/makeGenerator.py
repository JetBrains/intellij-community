async def gen() -> <warning descr="Expected type 'AsyncGenerator[Literal[\"str\"] | float, bool]', got 'str' instead">str</warning>:
    b: bool = <warning descr="Expected type 'str', got 'AsyncGenerator[Literal[\"str\"] | float, bool]' instead"><caret>yield "str"</warning>
    if b:
        b = <warning descr="Expected type 'str', got 'AsyncGenerator[Literal[\"str\"] | float, bool]' instead">yield 3.14</warning>
