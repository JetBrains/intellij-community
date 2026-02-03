from typing import Dict

data = {}  # type: Dict[int, str]
data[<warning descr="Expected type 'int' (matched generic type '_KT'), got 'str' instead">'test'</warning>] = <warning descr="Expected type 'str' (matched generic type '_VT'), got 'int' instead">12</warning>
data[12] = 'test'