from typing import Any

class AttrDef:
    name: Any
    key: Any
    validate: Any
    pre_query: Any
    post_query: Any
    default: Any
    dereference_dn: Any
    description: Any
    mandatory: Any
    single_value: Any
    oid_info: Any
    other_names: Any
    def __init__(
        self,
        name,
        key: Any | None = ...,
        validate: Any | None = ...,
        pre_query: Any | None = ...,
        post_query: Any | None = ...,
        default=...,
        dereference_dn: Any | None = ...,
        description: Any | None = ...,
        mandatory: bool = ...,
        single_value: Any | None = ...,
        alias: Any | None = ...,
    ) -> None: ...
    def __eq__(self, other): ...
    def __lt__(self, other): ...
    def __hash__(self): ...
    def __setattr__(self, key, value) -> None: ...
