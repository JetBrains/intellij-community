import sys

from typing import Any, Iterator, Mapping, Text, Tuple, TypeVar

_K = TypeVar('_K')
_V = TypeVar('_V')

PY2 = ...  # type: bool
def iteritems(d: Mapping[_K, _V]) -> Iterator[Tuple[_K, _V]]: ...
if sys.version_info[0] >= 3:
    text_type = str
    string_types = str,
    unichr = chr
    int_types = int,
else:
    text_type = unicode
    string_types = (str, unicode)
    unichr = __builtins__.unichr
    int_types = (int, long)
