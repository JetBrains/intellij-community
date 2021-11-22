from typing import Any

from jsonschema import exceptions as exceptions
from jsonschema.exceptions import ErrorTree as ErrorTree

class _DontDoThat(Exception): ...

validators: Any
meta_schemas: Any

def validates(version): ...

class _DefaultTypesDeprecatingMetaClass(type):
    DEFAULT_TYPES: Any

def create(
    meta_schema,
    validators=...,
    version: Any | None = ...,
    default_types: Any | None = ...,
    type_checker: Any | None = ...,
    id_of=...,
): ...
def extend(validator, validators=..., version: Any | None = ..., type_checker: Any | None = ...): ...

Draft3Validator: Any
Draft4Validator: Any
Draft6Validator: Any
Draft7Validator: Any

class RefResolver:
    referrer: Any
    cache_remote: Any
    handlers: Any
    store: Any
    def __init__(
        self,
        base_uri,
        referrer,
        store=...,
        cache_remote: bool = ...,
        handlers=...,
        urljoin_cache: Any | None = ...,
        remote_cache: Any | None = ...,
    ) -> None: ...
    @classmethod
    def from_schema(cls, schema, id_of=..., *args, **kwargs): ...
    def push_scope(self, scope) -> None: ...
    def pop_scope(self) -> None: ...
    @property
    def resolution_scope(self): ...
    @property
    def base_uri(self): ...
    def in_scope(self, scope) -> None: ...
    def resolving(self, ref) -> None: ...
    def resolve(self, ref): ...
    def resolve_from_url(self, url): ...
    def resolve_fragment(self, document, fragment): ...
    def resolve_remote(self, uri): ...

def validate(instance, schema, cls: Any | None = ..., *args, **kwargs) -> None: ...
def validator_for(schema, default=...): ...
