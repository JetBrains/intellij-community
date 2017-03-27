# Type declaration for a WSGI Function in Python 2
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

from typing import Callable, Dict, Iterable, List, Optional, Tuple, Type, Union
from types import TracebackType

_exc_info = Tuple[Optional[Type[BaseException]],
                  Optional[BaseException],
                  Optional[TracebackType]]
_Text = Union[unicode, str]
WSGIApplication = Callable[
    [
        Dict[_Text, _Text],
        Union[
            Callable[[_Text, List[Tuple[_Text, _Text]]], Callable[[_Text], None]],
            Callable[[_Text, List[Tuple[_Text, _Text]], _exc_info], Callable[[_Text], None]]
        ]
    ],
    Iterable[_Text]
]
