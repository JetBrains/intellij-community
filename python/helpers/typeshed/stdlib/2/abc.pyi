from typing import Any, Dict, Set, Tuple, Type
import _weakrefset

# NOTE: mypy has special processing for ABCMeta and abstractmethod.

def abstractmethod(funcobj: Any) -> Any: ...

class ABCMeta(type):
    # TODO: FrozenSet
    __abstractmethods__ = ...  # type: Set[Any]
    _abc_cache = ...  # type: _weakrefset.WeakSet
    _abc_invalidation_counter = ...  # type: int
    _abc_negative_cache = ...  # type: _weakrefset.WeakSet
    _abc_negative_cache_version = ...  # type: int
    _abc_registry = ...  # type: _weakrefset.WeakSet
    def __init__(self, name: str, bases: Tuple[type, ...], namespace: Dict[Any, Any]) -> None: ...
    def __instancecheck__(cls: "ABCMeta", instance: Any) -> Any: ...
    def __subclasscheck__(cls: "ABCMeta", subclass: Any) -> Any: ...
    def _dump_registry(cls: "ABCMeta", *args: Any, **kwargs: Any) -> None: ...
    def register(cls: "ABCMeta", subclass: Type[Any]) -> None: ...

# TODO: The real abc.abstractproperty inherits from "property".
class abstractproperty(object):
    def __new__(cls, func: Any) -> Any: ...
    __isabstractmethod__ = ...  # type: bool
    doc = ...  # type: Any
    fdel = ...  # type: Any
    fget = ...  # type: Any
    fset = ...  # type: Any
