# Better codecs stubs hand-written by o11c.
# https://docs.python.org/2/library/codecs.html
from typing import (
        BinaryIO,
        Callable,
        Iterable,
        Iterator,
        List,
        Tuple,
        Union,
)

from abc import abstractmethod


# TODO: this only satisfies the most common interface, where
# str is the raw form and unicode is the cooked form.
# In the long run, both should become template parameters maybe?
# There *are* str->str and unicode->unicode encodings in the standard library.
# And unlike python 3, they are in fairly widespread use.

_decoded = unicode
_encoded = str

# TODO: It is not possible to specify these signatures correctly, because
# they have an optional positional or keyword argument for errors=.
_encode_type = Callable[[_decoded], _encoded] # signature of Codec().encode
_decode_type = Callable[[_encoded], _decoded] # signature of Codec().decode
_stream_reader_type = Callable[[BinaryIO], 'StreamReader'] # signature of StreamReader __init__
_stream_writer_type = Callable[[BinaryIO], 'StreamWriter'] # signature of StreamWriter __init__
_incremental_encoder_type = Callable[[], 'IncrementalEncoder'] # signature of IncrementalEncoder __init__
_incremental_decode_type = Callable[[], 'IncrementalDecoder'] # signature of IncrementalDecoder __init__


def encode(obj: _decoded, encoding: str = ..., errors: str = ...) -> _encoded:
    ...
def decode(obj: _encoded, encoding: str = ..., errors: str = ...) -> _decoded:
    ...

def lookup(encoding: str) -> 'CodecInfo':
    ...
class CodecInfo(Tuple[_encode_type, _decode_type, _stream_reader_type, _stream_writer_type]):
    def __init__(self, encode: _encode_type, decode: _decode_type, streamreader: _stream_reader_type = ..., streamwriter: _stream_writer_type = ..., incrementalencoder: _incremental_encoder_type = ..., incrementaldecoder: _incremental_decode_type = ..., name: str = ...) -> None: ...
    encode = ...  # type: _encode_type
    decode = ...  # type: _decode_type
    streamreader = ...  # type: _stream_reader_type
    streamwriter = ...  # type: _stream_writer_type
    incrementalencoder = ...  # type: _incremental_encoder_type
    incrementaldecoder = ...  # type: _incremental_decode_type
    name = ...  # type: str

def getencoder(encoding: str) -> _encode_type:
    ...
def getdecoder(encoding: str) -> _encode_type:
    ...
def getincrementalencoder(encoding: str) -> _incremental_encoder_type:
    ...
def getincrementaldecoder(encoding: str) -> _incremental_encoder_type:
    ...
def getreader(encoding: str) -> _stream_reader_type:
    ...
def getwriter(encoding: str) -> _stream_writer_type:
    ...

def register(search_function: Callable[[str], CodecInfo]) -> None:
    ...

def open(filename: str, mode: str = ..., encoding: str = ..., errors: str = ..., buffering: int = ...) -> StreamReaderWriter:
    ...

def EncodedFile(file: BinaryIO, data_encoding: str, file_encoding: str = ..., errors = ...) -> 'StreamRecoder':
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
    buffer = ...  # type: str
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
    def __init__(self, stream: BinaryIO, errors: str = ...) -> None:
        ...
    def write(self, obj: _decoded) -> None:
        ...
    def writelines(self, list: List[str]) -> None:
        ...
    def reset(self) -> None:
        ...

class StreamReader(Codec):
    errors = ...  # type: str
    def __init__(self, stream: BinaryIO, errors: str = ...) -> None:
        ...
    def read(self, size: int = ..., chars: int = ..., firstline: bool = ...) -> _decoded:
        ...
    def readline(self, size: int = ..., keepends: bool = ...) -> _decoded:
        ...
    def readlines(self, sizehint: int = ..., keepends: bool = ...) -> List[_decoded]:
        ...
    def reset(self) -> None:
        ...

class StreamReaderWriter:
    def __init__(self, stream: BinaryIO, Reader: _stream_reader_type, Writer: _stream_writer_type, errors: str = ...) -> None:
        ...
    def __enter__(self) -> BinaryIO:
        ...
    def __exit__(self, typ, exc, tb) -> bool:
        ...

class StreamRecoder(BinaryIO):
    def __init__(self, stream: BinaryIO, encode: _encode_type, decode: _decode_type, Reader: _stream_reader_type, Writer: _stream_writer_type, errors: str = ...) -> None:
        ...
