async def gen() -> <warning descr="Expected type 'AsyncGenerator[Literal[\"str\"] | float, Unknown]', got 'str' instead">str</warning>:
    b: bool = <warning descr="Expected type 'str', got 'AsyncGenerator[Literal[\"str\"] | float, Unknown]' instead"><caret>yield "str"</warning>
    if b:
        b = <warning descr="Expected type 'str', got 'AsyncGenerator[Literal[\"str\"] | float, Unknown]' instead">yield 3.14</warning>