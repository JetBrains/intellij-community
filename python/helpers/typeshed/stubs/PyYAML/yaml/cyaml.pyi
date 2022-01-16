from _typeshed import SupportsRead
from typing import IO, Any, Mapping, Sequence, Text, Union

from yaml.constructor import BaseConstructor, FullConstructor, SafeConstructor, UnsafeConstructor
from yaml.events import Event
from yaml.nodes import Node
from yaml.representer import BaseRepresenter, SafeRepresenter
from yaml.resolver import BaseResolver, Resolver
from yaml.tokens import Token

_Readable = SupportsRead[Union[Text, bytes]]

class CParser:
    def __init__(self, stream: str | bytes | _Readable) -> None: ...
    def dispose(self) -> None: ...
    def get_token(self) -> Token | None: ...
    def peek_token(self) -> Token | None: ...
    def check_token(self, *choices) -> bool: ...
    def get_event(self) -> Event | None: ...
    def peek_event(self) -> Event | None: ...
    def check_event(self, *choices) -> bool: ...
    def check_node(self) -> bool: ...
    def get_node(self) -> Node | None: ...
    def get_single_node(self) -> Node | None: ...

class CBaseLoader(CParser, BaseConstructor, BaseResolver):
    def __init__(self, stream: str | bytes | _Readable) -> None: ...

class CLoader(CParser, SafeConstructor, Resolver):
    def __init__(self, stream: str | bytes | _Readable) -> None: ...

class CSafeLoader(CParser, SafeConstructor, Resolver):
    def __init__(self, stream: str | bytes | _Readable) -> None: ...

class CFullLoader(CParser, FullConstructor, Resolver):
    def __init__(self, stream: str | bytes | _Readable) -> None: ...

class CUnsafeLoader(CParser, UnsafeConstructor, Resolver):
    def __init__(self, stream: str | bytes | _Readable) -> None: ...

class CEmitter(object):
    def __init__(
        self,
        stream: IO[Any],
        canonical: Any | None = ...,
        indent: int | None = ...,
        width: int | None = ...,
        allow_unicode: Any | None = ...,
        line_break: str | None = ...,
        encoding: Text | None = ...,
        explicit_start: Any | None = ...,
        explicit_end: Any | None = ...,
        version: Sequence[int] | None = ...,
        tags: Mapping[Text, Text] | None = ...,
    ) -> None: ...

class CBaseDumper(CEmitter, BaseRepresenter, BaseResolver):
    def __init__(
        self,
        stream: IO[Any],
        default_style: str | None = ...,
        default_flow_style: bool | None = ...,
        canonical: Any | None = ...,
        indent: int | None = ...,
        width: int | None = ...,
        allow_unicode: Any | None = ...,
        line_break: str | None = ...,
        encoding: Text | None = ...,
        explicit_start: Any | None = ...,
        explicit_end: Any | None = ...,
        version: Sequence[int] | None = ...,
        tags: Mapping[Text, Text] | None = ...,
    ) -> None: ...

class CDumper(CEmitter, SafeRepresenter, Resolver): ...

CSafeDumper = CDumper
