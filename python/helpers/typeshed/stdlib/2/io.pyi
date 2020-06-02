# Stubs for io

# Based on https://docs.python.org/2/library/io.html

# Only a subset of functionality is included.

from typing import List, BinaryIO, TextIO, IO, overload, Iterator, Iterable, Any, Union, Optional
from typing_extensions import Literal
import _io

from _io import BlockingIOError as BlockingIOError
from _io import BufferedRWPair as BufferedRWPair
from _io import BufferedRandom as BufferedRandom
from _io import BufferedReader as BufferedReader
from _io import BufferedWriter as BufferedWriter
from _io import BytesIO as BytesIO
from _io import DEFAULT_BUFFER_SIZE as DEFAULT_BUFFER_SIZE
from _io import FileIO as FileIO
from _io import IncrementalNewlineDecoder as IncrementalNewlineDecoder
from _io import StringIO as StringIO
from _io import TextIOWrapper as TextIOWrapper
from _io import UnsupportedOperation as UnsupportedOperation
from _io import open as open

_OpenTextMode = Literal[
    'r', 'r+', '+r', 'rt', 'tr', 'rt+', 'r+t', '+rt', 'tr+', 't+r', '+tr',
    'w', 'w+', '+w', 'wt', 'tw', 'wt+', 'w+t', '+wt', 'tw+', 't+w', '+tw',
    'a', 'a+', '+a', 'at', 'ta', 'at+', 'a+t', '+at', 'ta+', 't+a', '+ta',
    'U', 'rU', 'Ur', 'rtU', 'rUt', 'Urt', 'trU', 'tUr', 'Utr',
]
_OpenBinaryModeUpdating = Literal[
    'rb+', 'r+b', '+rb', 'br+', 'b+r', '+br',
    'wb+', 'w+b', '+wb', 'bw+', 'b+w', '+bw',
    'ab+', 'a+b', '+ab', 'ba+', 'b+a', '+ba',
]
_OpenBinaryModeWriting = Literal[
    'wb', 'bw',
    'ab', 'ba',
]
_OpenBinaryModeReading = Literal[
    'rb', 'br',
    'rbU', 'rUb', 'Urb', 'brU', 'bUr', 'Ubr',
]
_OpenBinaryMode = Union[_OpenBinaryModeUpdating, _OpenBinaryModeReading, _OpenBinaryModeWriting]

def _OpenWrapper(file: Union[str, unicode, int],
                 mode: unicode = ..., buffering: int = ..., encoding: unicode = ...,
                 errors: unicode = ..., newline: unicode = ...,
                 closefd: bool = ...) -> IO[Any]: ...

SEEK_SET: int
SEEK_CUR: int
SEEK_END: int


class IOBase(_io._IOBase): ...

class RawIOBase(_io._RawIOBase, IOBase): ...

class BufferedIOBase(_io._BufferedIOBase, IOBase): ...

# Note: In the actual io.py, TextIOBase subclasses IOBase.
# (Which we don't do here because we don't want to subclass both TextIO and BinaryIO.)
class TextIOBase(_io._TextIOBase): ...
