from typing import Dict

data = {}  # type: Dict[int, str]
data[<warning descr="Expected type 'int', got 'str' instead">'test'</warning>] = <warning descr="Expected type 'str', got 'Literal[12]' instead">12</warning>
data[12] = 'test'