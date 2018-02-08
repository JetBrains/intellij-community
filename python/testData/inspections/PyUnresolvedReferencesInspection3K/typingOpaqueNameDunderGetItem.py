from typing import TypeVar, Tuple, Generic, Callable, Type, ClassVar, Union, Optional, List, Dict, \
    DefaultDict, Set, FrozenSet, Counter, Deque, ChainMap, Protocol

T = TypeVar("T")

# special forms
print(Tuple[T])
print(Generic[T])
print(Protocol[T])
print(Callable[[T], T])
print(Type[T])
print(ClassVar[T])

# aliases
print(Union[T])
print(Optional[T])
print(List[T])
print(Dict[T, T])
print(DefaultDict[T, T])
print(Set[T])
print(FrozenSet[T])
print(Counter[T])
print(Deque[T])
print(ChainMap[T, T])
