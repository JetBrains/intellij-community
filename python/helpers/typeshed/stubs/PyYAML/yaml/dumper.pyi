from _typeshed import SupportsWrite
from collections.abc import Mapping

from yaml.emitter import Emitter
from yaml.representer import BaseRepresenter, Representer, SafeRepresenter
from yaml.resolver import BaseResolver, Resolver
from yaml.serializer import Serializer

class BaseDumper(Emitter, Serializer, BaseRepresenter, BaseResolver):
    def __init__(
        self,
        stream: SupportsWrite[bytes | str],
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

class SafeDumper(Emitter, Serializer, SafeRepresenter, Resolver):
    def __init__(
        self,
        stream: SupportsWrite[bytes | str],
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

class Dumper(Emitter, Serializer, Representer, Resolver):
    def __init__(
        self,
        stream: SupportsWrite[bytes | str],
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
