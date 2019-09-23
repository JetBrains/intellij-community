# Stubs for urllib.parse
from typing import Any, List, Dict, Tuple, AnyStr, Generic, overload, Sequence, Mapping, Union, NamedTuple, Callable, Optional
import sys

_Str = Union[bytes, str]


uses_relative: List[str]
uses_netloc: List[str]
uses_params: List[str]
non_hierarchical: List[str]
uses_query: List[str]
uses_fragment: List[str]
scheme_chars: str
MAX_CACHE_SIZE = 0

class _ResultMixinBase(Generic[AnyStr]):
    def geturl(self) -> AnyStr: ...

class _ResultMixinStr(_ResultMixinBase[str]):
    def encode(self, encoding: str = ..., errors: str = ...) -> _ResultMixinBytes: ...


class _ResultMixinBytes(_ResultMixinBase[str]):
    def decode(self, encoding: str = ..., errors: str = ...) -> _ResultMixinStr: ...


class _NetlocResultMixinBase(Generic[AnyStr]):
    username: AnyStr
    password: AnyStr
    hostname: AnyStr
    port: int

class _NetlocResultMixinStr(_NetlocResultMixinBase[str], _ResultMixinStr): ...

class _NetlocResultMixinBytes(_NetlocResultMixinBase[bytes], _ResultMixinBytes): ...

class _DefragResultBase(tuple, Generic[AnyStr]):
    url: AnyStr
    fragment: AnyStr


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
def quote(string: str, safe: _Str = ..., encoding: str = ..., errors: str = ...) -> str: ...
@overload
def quote(string: bytes, safe: _Str = ...) -> str: ...

def quote_from_bytes(bs: bytes, safe: _Str = ...) -> str: ...

@overload
def quote_plus(string: str, safe: _Str = ..., encoding: str = ..., errors: str = ...) -> str: ...
@overload
def quote_plus(string: bytes, safe: _Str = ...) -> str: ...

def unquote(string: str, encoding: str = ..., errors: str = ...) -> str: ...

def unquote_to_bytes(string: _Str) -> bytes: ...

def unquote_plus(string: str, encoding: str = ..., errors: str = ...) -> str: ...

@overload
def urldefrag(url: str) -> DefragResult: ...
@overload
def urldefrag(url: bytes) -> DefragResultBytes: ...

def urlencode(
    query: Union[Mapping[Any, Any], Mapping[Any, Sequence[Any]], Sequence[Tuple[Any, Any]], Sequence[Tuple[Any, Sequence[Any]]]],
    doseq: bool = ...,
    safe: AnyStr = ...,
    encoding: str = ...,
    errors: str = ...,
    quote_via: Callable[[str, AnyStr, str, str], str] = ...,
) -> str: ...

def urljoin(base: AnyStr, url: Optional[AnyStr], allow_fragments: bool = ...) -> AnyStr: ...

@overload
def urlparse(url: str, scheme: str = ..., allow_fragments: bool = ...) -> ParseResult: ...
@overload
def urlparse(url: bytes, scheme: bytes = ..., allow_fragments: bool = ...) -> ParseResultBytes: ...

@overload
def urlsplit(url: str, scheme: str = ..., allow_fragments: bool = ...) -> SplitResult: ...
@overload
def urlsplit(url: bytes, scheme: bytes = ..., allow_fragments: bool = ...) -> SplitResultBytes: ...

@overload
def urlunparse(components: Tuple[AnyStr, AnyStr, AnyStr, AnyStr, AnyStr, AnyStr]) -> AnyStr: ...
@overload
def urlunparse(components: Sequence[AnyStr]) -> AnyStr: ...

@overload
def urlunsplit(components: Tuple[AnyStr, AnyStr, AnyStr, AnyStr, AnyStr]) -> AnyStr: ...
@overload
def urlunsplit(components: Sequence[AnyStr]) -> AnyStr: ...
