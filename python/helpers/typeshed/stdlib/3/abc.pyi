from typing import Any, Callable, Type, TypeVar
import sys
# Stubs for abc.

_T = TypeVar('_T')
_FuncT = TypeVar('_FuncT', bound=Callable[..., Any])

# Thesee definitions have special processing in mypy
class ABCMeta(type):
    if sys.version_info >= (3, 3):
        def register(cls: "ABCMeta", subclass: Type[_T]) -> Type[_T]: ...
    else:
        def register(cls: "ABCMeta", subclass: Type[Any]) -> None: ...

def abstractmethod(callable: _FuncT) -> _FuncT: ...
def abstractproperty(callable: _FuncT) -> _FuncT: ...
# These two are deprecated and not supported by mypy
def abstractstaticmethod(callable: _FuncT) -> _FuncT: ...
def abstractclassmethod(callable: _FuncT) -> _FuncT: ...

if sys.version_info >= (3, 4):
    class ABC(metaclass=ABCMeta):
        pass
    def get_cache_token() -> object: ...
