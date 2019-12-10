from typing import Type, TypeVar

T = TypeVar("T")

class mytime:
    if sys.version_info >= (3, 8):
        @classmethod
        def now(cls: Type[T], tz: Optional[int] = ...) -> T: ...
    else:
        @overload
        @classmethod
        def now(cls: Type[T], tz: int = ...) -> T: ...