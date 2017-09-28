import sys
import typing
from typing import ClassVar as ClassVar
from typing import ContextManager as ContextManager
from typing import Counter as Counter
from typing import DefaultDict as DefaultDict
from typing import Deque as Deque
from typing import NewType as NewType
from typing import NoReturn as NoReturn
from typing import overload as overload
from typing import Text as Text
from typing import Type as Type
from typing import TYPE_CHECKING as TYPE_CHECKING
from typing import TypeVar, Any

_TC = TypeVar('_TC', bound=Type[object])
class _SpecialForm:
    def __getitem__(self, typeargs: Any) -> Any: ...
def runtime(cls: _TC) -> _TC: ...
Protocol: _SpecialForm = ...

if sys.version_info >= (3, 3):
    from typing import ChainMap as ChainMap

if sys.version_info >= (3, 5):
    from typing import AsyncIterable as AsyncIterable
    from typing import AsyncIterator as AsyncIterator
    from typing import AsyncContextManager as AsyncContextManager
    from typing import Awaitable as Awaitable
    from typing import Coroutine as Coroutine

if sys.version_info >= (3, 6):
    from typing import AsyncGenerator as AsyncGenerator
