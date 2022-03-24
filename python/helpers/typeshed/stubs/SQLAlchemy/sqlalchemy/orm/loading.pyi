from typing import Any

def instances(cursor, context): ...
def merge_frozen_result(session, statement, frozen_result, load: bool = ...): ...
def merge_result(query, iterator, load: bool = ...): ...
def get_from_identity(session, mapper, key, passive): ...
def load_on_ident(
    session,
    statement,
    key,
    load_options: Any | None = ...,
    refresh_state: Any | None = ...,
    with_for_update: Any | None = ...,
    only_load_props: Any | None = ...,
    no_autoflush: bool = ...,
    bind_arguments=...,
    execution_options=...,
): ...
def load_on_pk_identity(
    session,
    statement,
    primary_key_identity,
    load_options: Any | None = ...,
    refresh_state: Any | None = ...,
    with_for_update: Any | None = ...,
    only_load_props: Any | None = ...,
    identity_token: Any | None = ...,
    no_autoflush: bool = ...,
    bind_arguments=...,
    execution_options=...,
): ...

class PostLoad:
    loaders: Any
    states: Any
    load_keys: Any
    def __init__(self) -> None: ...
    def add_state(self, state, overwrite) -> None: ...
    def invoke(self, context, path) -> None: ...
    @classmethod
    def for_context(cls, context, path, only_load_props): ...
    @classmethod
    def path_exists(cls, context, path, key): ...
    @classmethod
    def callable_for_path(cls, context, path, limit_to_mapper, token, loader_callable, *arg, **kw) -> None: ...

def load_scalar_attributes(mapper, state, attribute_names, passive) -> None: ...
