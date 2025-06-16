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
        key=None,
        validate=None,
        pre_query=None,
        post_query=None,
        default=...,
        dereference_dn=None,
        description=None,
        mandatory: bool = False,
        single_value=None,
        alias=None,
    ) -> None: ...
    def __eq__(self, other): ...
    def __lt__(self, other): ...
    def __hash__(self) -> int: ...
    def __setattr__(self, key: str, value) -> None: ...
