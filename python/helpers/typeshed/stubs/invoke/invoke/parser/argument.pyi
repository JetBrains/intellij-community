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
        name=None,
        names=(),
        kind=...,
        default=None,
        help=None,
        positional: bool = False,
        optional: bool = False,
        incrementable: bool = False,
        attr_name=None,
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
    def set_value(self, value, cast: bool = True): ...
    @property
    def got_value(self): ...
