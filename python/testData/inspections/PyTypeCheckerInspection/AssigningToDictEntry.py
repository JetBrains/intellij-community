from typing import Dict

data = {}  # type: Dict[int, str]
data[<weak_warning descr="Expected type 'int' (matched generic type '_KT'), got 'str' instead">'test'</weak_warning>] = <weak_warning descr="Expected type 'str' (matched generic type '_VT'), got 'int' instead">12</weak_warning>
data[12] = 'test'