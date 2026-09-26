from typing import Generator

def gen() -> Generator[int, bool, str]:
    b: bool = yield <warning descr="Expected yield type 'int', got 'Literal[\"str\"]' instead"><caret>"str"</warning>
    return <warning descr="Expected type 'str', got 'Literal[42]' instead">42</warning>