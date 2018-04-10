# Type declaration for a WSGI Function in Python 3
#
# wsgiref/types.py doesn't exist and neither does WSGIApplication, it's a type
# provided for type checking purposes.
#
# This means you cannot simply import wsgiref.types in your code. Instead,
# use the `TYPE_CHECKING` flag from the typing module:
#
#   from typing import TYPE_CHECKING
#
#   if TYPE_CHECKING:
#       from wsgiref.types import WSGIApplication
#
# This import is now only taken into account by the type checker. Consequently,
# you need to use 'WSGIApplication' and not simply WSGIApplication when type
# hinting your code.  Otherwise Python will raise NameErrors.

from typing import Callable, Dict, Iterable, List, Optional, Tuple, Type, Union, Any
from types import TracebackType

_exc_info = Tuple[Optional[Type[BaseException]],
                  Optional[BaseException],
                  Optional[TracebackType]]
WSGIEnvironment = Dict[str, Any]
WSGIApplication = Callable[
    [
        WSGIEnvironment,
        Union[
            Callable[[str, List[Tuple[str, str]]], Callable[[Union[bytes, str]], None]],
            Callable[[str, List[Tuple[str, str]], _exc_info], Callable[[Union[bytes, str]], None]]
        ]
    ],
    Iterable[Union[bytes, str]]
]
