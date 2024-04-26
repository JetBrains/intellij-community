from typing import Literal, Tuple, List, Set

L1 = Literal['test']
L2 = Literal['a', 'b', 5]

tuple_one_literal: Tuple[L1] = ('test',)
tuple_one_literal_incorrect: Tuple[L1] = <warning descr="Expected type 'tuple[Literal['test']]', got 'tuple[Literal['t']]' instead">('t',)</warning>

tuple_union_literal: Tuple[L2] = ('b',)
tuple_union_literal_incorrect: Tuple[L2] = <warning descr="Expected type 'tuple[Literal['a', 'b', 5]]', got 'tuple[Literal['t']]' instead">('t',)</warning>

tuple_several_literal: Tuple[L2, L1] = ('b', 'test')
tuple_several_literal_incorrect: Tuple[L2, L1] = <warning descr="Expected type 'tuple[Literal['a', 'b', 5], Literal['test']]', got 'tuple[Literal['a'], Literal['r']]' instead">('a', 'r')</warning>

tuple_of_list_and_tuple_set: Tuple[List[Tuple[L2, L1]], Set[L2]] = ([('b', 'test'), (5, 'test')], {'b', 5})
tuple_of_list_and_tuple_set_incorrect: Tuple[List[Tuple[L2, L1]], Set[L2]] = <warning descr="Expected type 'tuple[list[tuple[Literal['a', 'b', 5], Literal['test']]], set[Literal['a', 'b', 5]]]', got 'tuple[list[tuple[Literal['r'], Literal['test']] | tuple[Literal[5], Literal['test']]], set[Literal['b', 5]]]' instead">([('r', 'test'), (5, 'test')], {'b', 5})</warning>

FooTuple = Tuple[Literal["a", "b"], int]

def foo(param: int) -> FooTuple:
    return "a", param