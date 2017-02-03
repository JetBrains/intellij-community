# Stubs for urlparse (Python 2)

from typing import Dict, List, NamedTuple, Tuple, Sequence, Union, overload

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

def urlparse(url: Union[str, unicode], scheme: str = ...,
             allow_fragments: bool = ...) -> ParseResult: ...
def urlsplit(url: Union[str, unicode], scheme: str = ...,
             allow_fragments: bool = ...) -> SplitResult: ...
@overload
def urlunparse(data: Tuple[str, str, str, str, str, str]) -> str: ...
@overload
def urlunparse(data: Sequence[str]) -> str: ...
@overload
def urlunsplit(data: Tuple[str, str, str, str, str]) -> str: ...
@overload
def urlunsplit(data: Sequence[str]) -> str: ...
def urljoin(base: Union[str, unicode], url: Union[str, unicode],
            allow_fragments: bool = ...) -> str: ...
def urldefrag(url: Union[str, unicode]) -> str: ...
def unquote(s: str) -> str: ...
def parse_qs(qs: str, keep_blank_values: bool = ...,
             strict_parsing: bool = ...) -> Dict[str, List[str]]: ...
def parse_qsl(qs: str, keep_blank_values: int = ...,
              strict_parsing: bool = ...) -> List[Tuple[str, str]]: ...
