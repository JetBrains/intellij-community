# Better codecs stubs hand-written by o11c.
# https://docs.python.org/2/library/codecs.html and https://docs.python.org/3/library/codecs.html
import sys
from typing import (
    BinaryIO,
    Callable,
    IO,
    Iterable,
    Iterator,
    List,
    Optional,
    Text,
    TextIO,
    Tuple,
    Type,
    TypeVar,
    Union,
)

from abc import abstractmethod
import types


# TODO: this only satisfies the most common interface, where
# bytes (py2 str) is the raw form and str (py2 unicode) is the cooked form.
# In the long run, both should become template parameters maybe?
# There *are* bytes->bytes and str->str encodings in the standard library.
# They are much more common in Python 2 than in Python 3.
# Python 3.5 supposedly might change something there.

_decoded = Text
_encoded = bytes

# TODO: It is not possible to specify these signatures correctly, because
# they have an optional positional or keyword argument for errors=.
_encode_type = Callable[[_decoded], _encoded]  # signature of Codec().encode
_decode_type = Callable[[_encoded], _decoded]  # signature of Codec().decode
_stream_reader_type = Callable[[IO[_encoded]], 'StreamReader']  # signature of StreamReader __init__
_stream_writer_type = Callable[[IO[_encoded]], 'StreamWriter']  # signature of StreamWriter __init__
_incremental_encoder_type = Callable[[], 'IncrementalEncoder']  # signature of IncrementalEncoder __init__
_incremental_decoder_type = Callable[[], 'IncrementalDecoder']  # signature of IncrementalDecoder __init__


def encode(obj: _decoded, encoding: str = ..., errors: str = ...) -> _encoded:
    ...
def decode(obj: _encoded, encoding: str = ..., errors: str = ...) -> _decoded:
    ...

def lookup(encoding: str) -> 'CodecInfo':
    ...
class CodecInfo(Tuple[_encode_type, _decode_type, _stream_reader_type, _stream_writer_type]):
    encode = ...  # type: _encode_type
    decode = ...  # type: _decode_type
    streamreader = ...  # type: _stream_reader_type
    streamwriter = ...  # type: _stream_writer_type
    incrementalencoder = ...  # type: _incremental_encoder_type
    incrementaldecoder = ...  # type: _incremental_decoder_type
    name = ...  # type: str
    def __init__(self, encode: _encode_type, decode: _decode_type, streamreader: _stream_reader_type = ..., streamwriter: _stream_writer_type = ..., incrementalencoder: _incremental_encoder_type = ..., incrementaldecoder: _incremental_decoder_type = ..., name: str = ...) -> None: ...

def getencoder(encoding: str) -> _encode_type:
    ...
def getdecoder(encoding: str) -> _decode_type:
    ...
def getincrementalencoder(encoding: str) -> _incremental_encoder_type:
    ...
def getincrementaldecoder(encoding: str) -> _incremental_decoder_type:
    ...
def getreader(encoding: str) -> _stream_reader_type:
    ...
def getwriter(encoding: str) -> _stream_writer_type:
    ...

def register(search_function: Callable[[str], CodecInfo]) -> None:
    ...

def open(filename: str, mode: str = ..., encoding: str = ..., errors: str = ..., buffering: int = ...) -> StreamReaderWriter:
    ...

def EncodedFile(file: IO[_encoded], data_encoding: str, file_encoding: str = ..., errors: str = ...) -> 'StreamRecoder':
    ...

def iterencode(iterator: Iterable[_decoded], encoding: str, errors: str = ...) -> Iterator[_encoded]:
    ...
def iterdecode(iterator: Iterable[_encoded], encoding: str, errors: str = ...) -> Iterator[_decoded]:
    ...

BOM = b''
BOM_BE = b''
BOM_LE = b''
BOM_UTF8 = b''
BOM_UTF16 = b''
BOM_UTF16_BE = b''
BOM_UTF16_LE = b''
BOM_UTF32 = b''
BOM_UTF32_BE = b''
BOM_UTF32_LE = b''

# It is expected that different actions be taken depending on which of the
# three subclasses of `UnicodeError` is actually ...ed. However, the Union
# is still needed for at least one of the cases.
def register_error(name: str, error_handler: Callable[[UnicodeError], Tuple[Union[str, bytes], int]]) -> None:
    ...
def lookup_error(name: str) -> Callable[[UnicodeError], Tuple[Union[str, bytes], int]]:
    ...

def strict_errors(exception: UnicodeError) -> Tuple[Union[str, bytes], int]:
    ...
def replace_errors(exception: UnicodeError) -> Tuple[Union[str, bytes], int]:
    ...
def ignore_errors(exception: UnicodeError) -> Tuple[Union[str, bytes], int]:
    ...
def xmlcharrefreplace_errors(exception: UnicodeError) -> Tuple[Union[str, bytes], int]:
    ...
def backslashreplace_errors(exception: UnicodeError) -> Tuple[Union[str, bytes], int]:
    ...

class Codec:
    # These are sort of @abstractmethod but sort of not.
    # The StreamReader and StreamWriter subclasses only implement one.
    def encode(self, input: _decoded, errors: str = ...) -> Tuple[_encoded, int]:
        ...
    def decode(self, input: _encoded, errors: str = ...) -> Tuple[_decoded, int]:
        ...

class IncrementalEncoder:
    errors = ...  # type: str
    def __init__(self, errors: str = ...) -> None:
        ...
    @abstractmethod
    def encode(self, object: _decoded, final: bool = ...) -> _encoded:
        ...
    def reset(self) -> None:
        ...
    # documentation says int but str is needed for the subclass.
    def getstate(self) -> Union[int, _decoded]:
        ...
    def setstate(self, state: Union[int, _decoded]) -> None:
        ...

class IncrementalDecoder:
    errors = ...  # type: str
    def __init__(self, errors: str = ...) -> None:
        ...
    @abstractmethod
    def decode(self, object: _encoded, final: bool = ...) -> _decoded:
        ...
    def reset(self) -> None:
        ...
    def getstate(self) -> Tuple[_encoded, int]:
        ...
    def setstate(self, state: Tuple[_encoded, int]) -> None:
        ...

# These are not documented but used in encodings/*.py implementations.
class BufferedIncrementalEncoder(IncrementalEncoder):
    buffer = ...  # type: str
    def __init__(self, errors: str = ...) -> None:
        ...
    @abstractmethod
    def _buffer_encode(self, input: _decoded, errors: str, final: bool) -> _encoded:
        ...
    def encode(self, input: _decoded, final: bool = ...) -> _encoded:
        ...
class BufferedIncrementalDecoder(IncrementalDecoder):
    buffer = ...  # type: bytes
    def __init__(self, errors: str = ...) -> None:
        ...
    @abstractmethod
    def _buffer_decode(self, input: _encoded, errors: str, final: bool) -> Tuple[_decoded, int]:
        ...
    def decode(self, object: _encoded, final: bool = ...) -> _decoded:
        ...

# TODO: it is not possible to specify the requirement that all other
# attributes and methods are passed-through from the stream.
class StreamWriter(Codec):
    errors = ...  # type: str
    def __init__(self, stream: IO[_encoded], errors: str = ...) -> None:
        ...
    def write(self, obj: _decoded) -> None:
        ...
    def writelines(self, list: Iterable[_decoded]) -> None:
        ...
    def reset(self) -> None:
        ...

class StreamReader(Codec):
    errors = ...  # type: str
    def __init__(self, stream: IO[_encoded], errors: str = ...) -> None:
        ...
    def read(self, size: int = ..., chars: int = ..., firstline: bool = ...) -> _decoded:
        ...
    def readline(self, size: int = ..., keepends: bool = ...) -> _decoded:
        ...
    def readlines(self, sizehint: int = ..., keepends: bool = ...) -> List[_decoded]:
        ...
    def reset(self) -> None:
        ...

_T = TypeVar('_T', bound='StreamReaderWriter')

# Doesn't actually inherit from TextIO, but wraps a BinaryIO to provide text reading and writing
# and delegates attributes to the underlying binary stream with __getattr__.
class StreamReaderWriter(TextIO):
    def __init__(self, stream: IO[_encoded], Reader: _stream_reader_type, Writer: _stream_writer_type, errors: str = ...) -> None: ...
    def read(self, size: int= ...) -> _decoded: ...
    def readline(self, size: Optional[int] = ...) -> _decoded: ...
    def readlines(self, sizehint: Optional[int] = ...) -> List[_decoded]: ...
    def __next__(self) -> _decoded: ...
    def __iter__(self: _T) -> _T: ...
    # This actually returns None, but that's incompatible with the supertype
    def write(self, data: _decoded) -> int: ...
    def writelines(self, list: Iterable[_decoded]) -> None: ...
    def reset(self) -> None: ...
    # Same as write()
    def seek(self, offset: int, whence: int = ...) -> int: ...
    def __enter__(self: _T) -> _T: ...
    def __exit__(self, typ: Optional[Type[BaseException]], exc: Optional[BaseException], tb: Optional[types.TracebackType]) -> bool: ...

class StreamRecoder(BinaryIO):
    def __init__(self, stream: IO[_encoded], encode: _encode_type, decode: _decode_type, Reader: _stream_reader_type, Writer: _stream_writer_type, errors: str = ...) -> None:
        ...
