# Stubs for urlparse (Python 2)

from typing import AnyStr, Dict, List, NamedTuple, Tuple, Sequence, Union, overload

_String = Union[str, unicode]

uses_relative = ...  # type: List[str]
uses_netloc = ...  # type: List[str]
uses_params = ...  # type: List[str]
non_hierarchical = ...  # type: List[str]
uses_query = ...  # type: List[str]
uses_fragment = ...  # type: List[str]
scheme_chars = ...  # type: str
MAX_CACHE_SIZE = 0

def clear_cache() -> None: ...

class ResultMixin(object):
    @property
    def username(self) -> str: ...
    @property
    def password(self) -> str: ...
    @property
    def hostname(self) -> str: ...
    @property
    def port(self) -> int: ...

class SplitResult(
    NamedTuple(
        'SplitResult',
        [
            ('scheme', str), ('netloc', str), ('path', str), ('query', str), ('fragment', str)
        ]
    ),
    ResultMixin
):
    def geturl(self) -> str: ...

class ParseResult(
    NamedTuple(
        'ParseResult',
        [
            ('scheme', str), ('netloc', str), ('path', str), ('params', str), ('query', str),
            ('fragment', str)
        ]
    ),
    ResultMixin
):
    def geturl(self) -> str: ...

def urlparse(url: _String, scheme: _String = ...,
             allow_fragments: bool = ...) -> ParseResult: ...
def urlsplit(url: _String, scheme: _String = ...,
             allow_fragments: bool = ...) -> SplitResult: ...
@overload
def urlunparse(data: Tuple[_String, _String, _String, _String, _String, _String]) -> str: ...
@overload
def urlunparse(data: Sequence[_String]) -> str: ...
@overload
def urlunsplit(data: Tuple[_String, _String, _String, _String, _String]) -> str: ...
@overload
def urlunsplit(data: Sequence[_String]) -> str: ...
def urljoin(base: _String, url: _String,
            allow_fragments: bool = ...) -> str: ...
def urldefrag(url: AnyStr) -> Tuple[AnyStr, str]: ...
def unquote(s: AnyStr) -> AnyStr: ...
def parse_qs(qs: AnyStr, keep_blank_values: bool = ...,
             strict_parsing: bool = ...) -> Dict[AnyStr, List[AnyStr]]: ...
def parse_qsl(qs: AnyStr, keep_blank_values: int = ...,
              strict_parsing: bool = ...) -> List[Tuple[AnyStr, AnyStr]]: ...
