from typing import Tuple, Literal, Dict, List

L1 = Literal['k1', 'k2']
L2 = Literal['a', 'b', 5]

d1: Dict[L1, L2] = {'k1': 'b', 'k2': 5}
d2: Dict[L1, L2] = <warning descr="Expected type 'dict[Literal['k1', 'k2'], Literal['a', 'b', 5]]', got 'dict[Literal['k1'], Literal['r']]' instead">{'k1': 'r'}</warning>
d3: Dict[L1, L2] = <warning descr="Expected type 'dict[Literal['k1', 'k2'], Literal['a', 'b', 5]]', got 'dict[Literal['r'], Literal['b']]' instead">{'r': 'b'}</warning>
d4: Dict[L1, List[L2]] = {'k2': ['b', 5]}
d5: Dict[L1, List[L2]] = <warning descr="Expected type 'dict[Literal['k1', 'k2'], list[Literal['a', 'b', 5]]]', got 'dict[Literal['k2'], list[Literal['r', 5]]]' instead">{'k2': ['r', 5]}</warning>
d6: Dict[L1, Tuple[L2, L2]] = {'k2': ('a', 5)}
d7: Dict[L1, Tuple[L2, L2]] = <warning descr="Expected type 'dict[Literal['k1', 'k2'], tuple[Literal['a', 'b', 5], Literal['a', 'b', 5]]]', got 'dict[Literal['k2'], tuple[Literal['r'], Literal[5]]]' instead">{'k2': ('r', 5)}</warning>
d8: Dict[L1, Dict[L1, L2]] = {'k1': {'k2': 'a', 'k1': 5}}
d9: Dict[L1, Dict[L1, L2]] = <warning descr="Expected type 'dict[Literal['k1', 'k2'], dict[Literal['k1', 'k2'], Literal['a', 'b', 5]]]', got 'dict[Literal['k1'], dict[Literal['k2', 'k1'], Literal['r', 9]]]' instead">{'k1': {'k2': 'r', 'k1': 9}}</warning>