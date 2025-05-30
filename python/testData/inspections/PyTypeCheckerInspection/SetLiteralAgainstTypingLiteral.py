from typing import Literal, Set, List, Union, Tuple

L1 = Literal['test']
L2 = Literal['a', 'b', 5]

set_one_literal: Set[L1] = {'test', 'test'}
set_one_literal_incorrect: Set[L1] = <warning descr="Expected type 'set[Literal['test']]', got 'set[Literal['r', 'a']]' instead">{'r', 'a'}</warning>

set_several_literal: Set[L2] = {'b', 5}
set_several_literal_incorrect: Set[L2] = <warning descr="Expected type 'set[Literal['a', 'b', 5]]', got 'set[Literal['r', 'a']]' instead">{'r', 'a'}</warning>
set_several_literal_incorrect2: Set[L2] = <warning descr="Expected type 'set[Literal['a', 'b', 5]]', got 'set[Literal['a', 'r']]' instead">{'a', 'r'}</warning>

set_union_literal: Set[Union[L2, L1]] = {'b', 'test', 5}
set_union_literal_incorrect: Set[Union[L2, L1]] = <warning descr="Expected type 'set[Literal['a', 'b', 5, 'test']]', got 'set[Literal['r', 'a']]' instead">{'r', 'a'}</warning>
set_union_literal_incorrect2: Set[Union[L2, L1]] = <warning descr="Expected type 'set[Literal['a', 'b', 5, 'test']]', got 'set[Literal['a', 'r']]' instead">{'a', 'r'}</warning>

set_of_tuple_and_list: Set[Union[Tuple[L2, L1], List[L1]]] = {('b', 'test'), ['test', 'test'], (5, 'test')}
set_of_tuple_and_list_incorrect: Set[Union[Tuple[L2, L1], List[L1]]] = <warning descr="Expected type 'set[tuple[Literal['a', 'b', 5], Literal['test']] | list[Literal['test']]]', got 'set[tuple[Literal['b'], Literal['r']] | list[Literal['test']] | tuple[Literal[5], Literal['test']]]' instead">{('b', 'r'), ['test', 'test'], (5, 'test')}</warning>