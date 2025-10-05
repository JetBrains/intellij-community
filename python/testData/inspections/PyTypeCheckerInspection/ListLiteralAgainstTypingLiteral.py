from typing import Literal, Tuple, List, Union

L1 = Literal['test']
L2 = Literal['a', 'b', 5]

list_one_literal: List[L1] = ['test', 'test']
list_one_literal_incorrect: List[L1] = <warning descr="Expected type 'list[Literal['test']]', got 'list[Literal['r', 'a']]' instead">['r', 'a']</warning>

list_several_literal: List[L2] = ['a', 5]
list_several_literal_incorrect: List[L2] = <warning descr="Expected type 'list[Literal['a', 'b', 5]]', got 'list[Literal['r', 'a']]' instead">['r', 'a']</warning>
list_several_literal_incorrect: List[L2] = <warning descr="Expected type 'list[Literal['a', 'b', 5]]', got 'list[Literal['a', 'r']]' instead">['a', 'r']</warning>

list_union_literal: List[Union[L2, L1]] = ['b', 'test', 5]
list_union_literal_incorrect: List[Union[L2, L1]] = <warning descr="Expected type 'list[Literal['a', 'b', 5, 'test']]', got 'list[Literal['r', 'a']]' instead">['r', 'a']</warning>
list_union_literal_incorrect: List[Union[L2, L1]] = <warning descr="Expected type 'list[Literal['a', 'b', 5, 'test']]', got 'list[Literal['a', 'r']]' instead">['a', 'r']</warning>

list_tuple: List[Tuple[L2, L1]] = [('b', 'test'), (5, 'test')]
list_tuple_incorrect: List[Tuple[L2, L1]] = <warning descr="Expected type 'list[tuple[Literal['a', 'b', 5], Literal['test']]]', got 'list[tuple[Literal['a']] | tuple[Literal[5], Literal['test']]]' instead">[('a',), (5, 'test')]</warning>