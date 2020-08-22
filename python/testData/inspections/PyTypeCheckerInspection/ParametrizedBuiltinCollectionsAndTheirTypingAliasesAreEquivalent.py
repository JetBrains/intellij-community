from typing import Dict, FrozenSet, List, Set, Tuple


def expects_builtin_list(xs: list[int]):
    expects_typing_List(xs)
    expects_typing_List(<warning descr="Expected type 'list[int]', got 'list[str]' instead">['a']</warning>)


def expects_typing_List(xs: List[int]):
    expects_builtin_list(xs)
    expects_builtin_list(<warning descr="Expected type 'list[int]', got 'list[str]' instead">['a']</warning>)


def expects_builtin_set(xs: set[int]):
    expects_typing_Set(xs)
    expects_typing_Set(<warning descr="Expected type 'set[int]', got 'set[str]' instead">{'a'}</warning>)


def expects_typing_Set(xs: Set[int]):
    expects_builtin_set(xs)
    expects_builtin_set(<warning descr="Expected type 'set[int]', got 'set[str]' instead">{'a'}</warning>)


def expects_builtin_frozenset(xs: frozenset[int]):
    expects_typing_FrozenSet(xs)
    expects_typing_FrozenSet(<warning descr="Expected type 'frozenset[int]', got 'frozenset[str]' instead">frozenset(['a'])</warning>)


def expects_typing_FrozenSet(xs: FrozenSet[int]):
    expects_builtin_frozenset(xs)
    expects_builtin_frozenset(<warning descr="Expected type 'frozenset[int]', got 'frozenset[str]' instead">frozenset(['a'])</warning>)


def expects_builtin_dict(xs: dict[str, int]):
    expects_typing_Dict(xs)
    expects_typing_Dict(<warning descr="Expected type 'dict[str, int]', got 'dict[int, str]' instead">{42: 'a'}</warning>)


def expects_typing_Dict(xs: Dict[str, int]):
    expects_builtin_dict(xs)
    expects_builtin_dict(<warning descr="Expected type 'dict[str, int]', got 'dict[int, str]' instead">{42: 'a'}</warning>)


def expects_builtin_tuple(xs: tuple[str, int]):
    expects_typing_Tuple(xs)
    expects_typing_Tuple(<warning descr="Expected type 'tuple[str, int]', got 'tuple[int, str]' instead">(42, 'a')</warning>)


def expects_typing_Tuple(xs: Tuple[str, int]):
    expects_builtin_tuple(xs)
    expects_builtin_tuple(<warning descr="Expected type 'tuple[str, int]', got 'tuple[int, str]' instead">(42, 'a')</warning>)
