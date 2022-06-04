from typing import Any

class Argument:
    names: Any
    kind: Any
    raw_value: Any
    default: Any
    help: Any
    positional: Any
    optional: Any
    incrementable: Any
    attr_name: Any
    def __init__(
        self,
        name=...,
        names=...,
        kind=...,
        default=...,
        help=...,
        positional: bool = ...,
        optional: bool = ...,
        incrementable: bool = ...,
        attr_name=...,
    ) -> None: ...
    @property
    def name(self): ...
    @property
    def nicknames(self): ...
    @property
    def takes_value(self): ...
    @property
    def value(self): ...
    @value.setter
    def value(self, arg) -> None: ...
    def set_value(self, value, cast: bool = ...): ...
    @property
    def got_value(self): ...
