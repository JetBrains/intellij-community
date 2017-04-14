# Stubs for urllib.parse
from typing import Any, List, Dict, Tuple, AnyStr, Generic, overload, Sequence, Mapping, Union, NamedTuple, Callable
import sys

__all__ = (
    'urlparse',
    'urlunparse',
    'urljoin',
    'urldefrag',
    'urlsplit',
    'urlunsplit',
    'urlencode',
    'parse_qs',
    'parse_qsl',
    'quote',
    'quote_plus',
    'quote_from_bytes',
    'unquote',
    'unquote_plus',
    'unquote_to_bytes'
)

uses_relative = ...  # type: List[str]
uses_netloc = ...  # type: List[str]
uses_params = ...  # type: List[str]
non_hierarchical = ...  # type: List[str]
uses_query = ...  # type: List[str]
uses_fragment = ...  # type: List[str]
scheme_chars = ...  # type: str
MAX_CACHE_SIZE = 0

class _ResultMixinBase(Generic[AnyStr]):
    def geturl(self) -> AnyStr: ...

class _ResultMixinStr(_ResultMixinBase[str]):
    def encode(self, encoding: str = ..., errors: str = ...) -> '_ResultMixinBytes': ...


class _ResultMixinBytes(_ResultMixinBase[str]):
    def decode(self, encoding: str = ..., errors: str = ...) -> '_ResultMixinStr': ...


class _NetlocResultMixinBase(Generic[AnyStr]):
    username = ...  # type: AnyStr
    password = ...  # type: AnyStr
    hostname = ...  # type: AnyStr
    port = ...  # type: int

class _NetlocResultMixinStr(_NetlocResultMixinBase[str], _ResultMixinStr): ...


class _NetlocResultMixinBytes(_NetlocResultMixinBase[str], _ResultMixinBytes): ...

class _DefragResultBase(tuple, Generic[AnyStr]):
    url = ...  # type: AnyStr
    fragment = ...  # type: AnyStr


_SplitResultBase = NamedTuple(
    '_SplitResultBase',
    [
        ('scheme', str), ('netloc', str), ('path', str), ('query', str), ('fragment', str)
    ]
)
_SplitResultBytesBase = NamedTuple(
    '_SplitResultBytesBase',
    [
        ('scheme', bytes), ('netloc', bytes), ('path', bytes), ('query', bytes), ('fragment', bytes)
    ]
)

_ParseResultBase = NamedTuple(
    '_ParseResultBase',
    [
        ('scheme', str), ('netloc', str), ('path', str), ('params', str), ('query', str), ('fragment', str)
    ]
)
_ParseResultBytesBase = NamedTuple(
    '_ParseResultBytesBase',
    [
        ('scheme', bytes), ('netloc', bytes), ('path', bytes), ('params', bytes), ('query', bytes), ('fragment', bytes)
    ]
)

# Structured result objects for string data
class DefragResult(_DefragResultBase[str], _ResultMixinStr): ...

class SplitResult(_SplitResultBase, _NetlocResultMixinStr): ...

class ParseResult(_ParseResultBase, _NetlocResultMixinStr): ...

# Structured result objects for bytes data
class DefragResultBytes(_DefragResultBase[bytes], _ResultMixinBytes): ...

class SplitResultBytes(_SplitResultBytesBase, _NetlocResultMixinBytes): ...

class ParseResultBytes(_ParseResultBytesBase, _NetlocResultMixinBytes): ...


def parse_qs(qs: AnyStr, keep_blank_values: bool = ..., strict_parsing: bool = ..., encoding: str = ..., errors: str = ...) -> Dict[AnyStr, List[AnyStr]]: ...

def parse_qsl(qs: AnyStr, keep_blank_values: bool = ..., strict_parsing: bool = ..., encoding: str = ..., errors: str = ...) -> List[Tuple[AnyStr, AnyStr]]: ...


@overload
def quote(string: str, safe: AnyStr = ..., encoding: str = ..., errors: str = ...) -> str: ...
@overload
def quote(string: bytes, safe: AnyStr = ...) -> str: ...

def quote_from_bytes(bs: bytes, safe: AnyStr = ...) -> str: ...

@overload
def quote_plus(string: str, safe: AnyStr = ..., encoding: str = ..., errors: str = ...) -> str: ...
@overload
def quote_plus(string: bytes, safe: AnyStr = ...) -> str: ...

def unquote(string: str, encoding: str = ..., errors: str = ...) -> str: ...

def unquote_to_bytes(string: AnyStr) -> bytes: ...

def unquote_plus(string: str, encoding: str = ..., errors: str = ...) -> str: ...

@overload
def urldefrag(url: str) -> DefragResult: ...
@overload
def urldefrag(url: bytes) -> DefragResultBytes: ...

if sys.version_info >= (3, 5):
    def urlencode(query: Union[Mapping[Any, Any],
                               Mapping[Any, Sequence[Any]],
                               Sequence[Tuple[Any, Any]],
                               Sequence[Tuple[Any, Sequence[Any]]]],
                  doseq: bool = ..., safe: AnyStr = ..., encoding: str = ..., errors: str = ...,
                  quote_via: Callable[[str, AnyStr, str, str], str] = ...) -> str: ...
else:
    def urlencode(query: Union[Mapping[Any, Any],
                               Mapping[Any, Sequence[Any]],
                               Sequence[Tuple[Any, Any]],
                               Sequence[Tuple[Any, Sequence[Any]]]],
                  doseq: bool = ..., safe: AnyStr = ..., encoding: str = ..., errors: str = ...) -> str: ...

def urljoin(base: AnyStr, url: AnyStr, allow_fragments: bool = ...) -> AnyStr: ...

@overload
def urlparse(url: str, scheme: str = ..., allow_fragments: bool = ...) -> ParseResult: ...
@overload
def urlparse(url: bytes, scheme: bytes = ..., allow_fragments: bool = ...) -> ParseResultBytes: ...

@overload
def urlsplit(url: str, scheme: str = ..., allow_fragments: bool = ...) -> SplitResult: ...
@overload
def urlsplit(url: bytes, scheme: bytes = ..., allow_fragments: bool = ...) -> SplitResultBytes: ...

@overload
def urlunparse(components: Sequence[AnyStr]) -> AnyStr: ...
@overload
def urlunparse(components: Tuple[AnyStr, AnyStr, AnyStr, AnyStr, AnyStr, AnyStr]) -> AnyStr: ...

@overload
def urlunsplit(components: Sequence[AnyStr]) -> AnyStr: ...
@overload
def urlunsplit(components: Tuple[AnyStr, AnyStr, AnyStr, AnyStr, AnyStr]) -> AnyStr: ...
