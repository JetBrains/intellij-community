# Type declaration for a WSGI Function
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

import sys
from typing import Callable, Dict, Iterable, List, Optional, Tuple, Type, Union, Any
from types import TracebackType

_exc_info = Tuple[Optional[Type[BaseException]],
                  Optional[BaseException],
                  Optional[TracebackType]]
if sys.version_info < (3,):
    _Text = Union[unicode, str]
    _BText = _Text
else:
    _Text = str
    _BText = Union[bytes, str]
WSGIEnvironment = Dict[_Text, Any]
WSGIApplication = Callable[
    [
        WSGIEnvironment,
        Union[
            Callable[[_Text, List[Tuple[_Text, _Text]]], Callable[[_BText], None]],
            Callable[[_Text, List[Tuple[_Text, _Text]], _exc_info], Callable[[_BText], None]]
        ]
    ],
    Iterable[_BText]
]
