from _typeshed import ConvertibleToInt
from collections.abc import Callable
from typing import Literal, overload

def require_string(value: object, value_name: str) -> None: ...  # raise TypeError if value is not string
def require_callback(
    callback: object, callback_name: str = "callback"  # raise TypeError if callback is not callable
) -> None: ...

@overload
def rpc_completion_callback(callback: None) -> Literal[True]: ...
@overload
def rpc_completion_callback(callback: Callable[..., object]) -> Literal[False]: ...

def zero_or_greater(name: str, value: ConvertibleToInt) -> None: ...
