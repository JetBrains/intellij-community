from typing import Dict, Literal

def foo(data: Dict[Literal['a', 5], bool]):
    a = data['a']
    five = data[5]
    b = data[<warning descr="Expected type 'Literal['a', 5]' (matched generic type '_KT'), got 'Literal['b']' instead">'b'</warning>]