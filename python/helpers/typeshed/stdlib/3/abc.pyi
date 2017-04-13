from typing import Any, Type, TypeVar
import sys
# Stubs for abc.

_T = TypeVar('_T')

# Thesee definitions have special processing in type checker.
class ABCMeta(type):
    if sys.version_info >= (3, 3):
        def register(cls: "ABCMeta", subclass: Type[_T]) -> Type[_T]: ...
    else:
        def register(cls: "ABCMeta", subclass: Type[Any]) -> None: ...
abstractmethod = object()
abstractproperty = object()

if sys.version_info >= (3, 4):
    class ABC(metaclass=ABCMeta):
        pass
