from collections.abc import Callable, Iterable, Iterator, Mapping, Sequence
from typing import Any, Pattern, TypeVar, overload

from . import resolver as resolver  # Help mypy a bit; this is implied by loader and dumper
from .constructor import BaseConstructor
from .cyaml import *
from .dumper import *
from .emitter import _WriteStream
from .error import *
from .events import *
from .loader import *
from .nodes import *
from .reader import _ReadStream
from .representer import BaseRepresenter
from .resolver import BaseResolver
from .tokens import *

# FIXME: the functions really return str if encoding is None, otherwise bytes. Waiting for python/mypy#5621
_Yaml = Any

__with_libyaml__: Any
__version__: str

_T = TypeVar("_T")
_Constructor = TypeVar("_Constructor", bound=BaseConstructor)
_Representer = TypeVar("_Representer", bound=BaseRepresenter)

def warnings(settings=...): ...
def scan(stream, Loader=...): ...
def parse(stream, Loader=...): ...
def compose(stream, Loader=...): ...
def compose_all(stream, Loader=...): ...
def load(stream: _ReadStream, Loader) -> Any: ...
def load_all(stream: _ReadStream, Loader) -> Iterator[Any]: ...
def full_load(stream: _ReadStream) -> Any: ...
def full_load_all(stream: _ReadStream) -> Iterator[Any]: ...
def safe_load(stream: _ReadStream) -> Any: ...
def safe_load_all(stream: _ReadStream) -> Iterator[Any]: ...
def unsafe_load(stream: _ReadStream) -> Any: ...
def unsafe_load_all(stream: _ReadStream) -> Iterator[Any]: ...
def emit(
    events,
    stream: _WriteStream[Any] | None = ...,
    Dumper=...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
): ...
@overload
def serialize_all(
    nodes,
    stream: _WriteStream[Any],
    Dumper=...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
) -> None: ...
@overload
def serialize_all(
    nodes,
    stream: None = ...,
    Dumper=...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
) -> _Yaml: ...
@overload
def serialize(
    node,
    stream: _WriteStream[Any],
    Dumper=...,
    *,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
) -> None: ...
@overload
def serialize(
    node,
    stream: None = ...,
    Dumper=...,
    *,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
) -> _Yaml: ...
@overload
def dump_all(
    documents: Sequence[Any],
    stream: _WriteStream[Any],
    Dumper=...,
    default_style: str | None = ...,
    default_flow_style: bool | None = ...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
    sort_keys: bool = ...,
) -> None: ...
@overload
def dump_all(
    documents: Sequence[Any],
    stream: None = ...,
    Dumper=...,
    default_style: str | None = ...,
    default_flow_style: bool | None = ...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
    sort_keys: bool = ...,
) -> _Yaml: ...
@overload
def dump(
    data: Any,
    stream: _WriteStream[Any],
    Dumper=...,
    *,
    default_style: str | None = ...,
    default_flow_style: bool | None = ...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
    sort_keys: bool = ...,
) -> None: ...
@overload
def dump(
    data: Any,
    stream: None = ...,
    Dumper=...,
    *,
    default_style: str | None = ...,
    default_flow_style: bool | None = ...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
    sort_keys: bool = ...,
) -> _Yaml: ...
@overload
def safe_dump_all(
    documents: Sequence[Any],
    stream: _WriteStream[Any],
    *,
    default_style: str | None = ...,
    default_flow_style: bool | None = ...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
    sort_keys: bool = ...,
) -> None: ...
@overload
def safe_dump_all(
    documents: Sequence[Any],
    stream: None = ...,
    *,
    default_style: str | None = ...,
    default_flow_style: bool | None = ...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
    sort_keys: bool = ...,
) -> _Yaml: ...
@overload
def safe_dump(
    data: Any,
    stream: _WriteStream[Any],
    *,
    default_style: str | None = ...,
    default_flow_style: bool | None = ...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
    sort_keys: bool = ...,
) -> None: ...
@overload
def safe_dump(
    data: Any,
    stream: None = ...,
    *,
    default_style: str | None = ...,
    default_flow_style: bool | None = ...,
    canonical: bool | None = ...,
    indent: int | None = ...,
    width: int | None = ...,
    allow_unicode: bool | None = ...,
    line_break: str | None = ...,
    encoding: str | None = ...,
    explicit_start: bool | None = ...,
    explicit_end: bool | None = ...,
    version: tuple[int, int] | None = ...,
    tags: Mapping[str, str] | None = ...,
    sort_keys: bool = ...,
) -> _Yaml: ...
def add_implicit_resolver(
    tag: str,
    regexp: Pattern[str],
    first: Iterable[Any] | None = ...,
    Loader: type[BaseResolver] | None = ...,
    Dumper: type[BaseResolver] = ...,
) -> None: ...
def add_path_resolver(
    tag: str,
    path: Iterable[Any],
    kind: type[Any] | None = ...,
    Loader: type[BaseResolver] | None = ...,
    Dumper: type[BaseResolver] = ...,
) -> None: ...
@overload
def add_constructor(
    tag: str, constructor: Callable[[Loader | FullLoader | UnsafeLoader, Node], Any], Loader: None = ...
) -> None: ...
@overload
def add_constructor(tag: str, constructor: Callable[[_Constructor, Node], Any], Loader: type[_Constructor]) -> None: ...
@overload
def add_multi_constructor(
    tag_prefix: str, multi_constructor: Callable[[Loader | FullLoader | UnsafeLoader, str, Node], Any], Loader: None = ...
) -> None: ...
@overload
def add_multi_constructor(
    tag_prefix: str, multi_constructor: Callable[[_Constructor, str, Node], Any], Loader: type[_Constructor]
) -> None: ...
@overload
def add_representer(data_type: type[_T], representer: Callable[[Dumper, _T], Node]) -> None: ...
@overload
def add_representer(data_type: type[_T], representer: Callable[[_Representer, _T], Node], Dumper: type[_Representer]) -> None: ...
@overload
def add_multi_representer(data_type: type[_T], multi_representer: Callable[[Dumper, _T], Node]) -> None: ...
@overload
def add_multi_representer(
    data_type: type[_T], multi_representer: Callable[[_Representer, _T], Node], Dumper: type[_Representer]
) -> None: ...

class YAMLObjectMetaclass(type):
    def __init__(cls, name, bases, kwds) -> None: ...

class YAMLObject(metaclass=YAMLObjectMetaclass):
    yaml_loader: Any
    yaml_dumper: Any
    yaml_tag: Any
    yaml_flow_style: Any
    @classmethod
    def from_yaml(cls, loader, node): ...
    @classmethod
    def to_yaml(cls, dumper, data): ...
