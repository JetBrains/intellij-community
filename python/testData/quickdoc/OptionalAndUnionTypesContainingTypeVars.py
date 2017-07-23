from typing import TypeVar, Optional, Union, Tuple, Any


T = TypeVar('T', int)


def f(x1: Optional[T], x2: Union[T, Tuple[Any, Any]]):
    print(x1, x2)


<the_ref>f
