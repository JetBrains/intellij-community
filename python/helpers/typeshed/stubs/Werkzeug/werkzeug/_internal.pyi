from typing import Any

class _Missing:
    def __reduce__(self): ...

class _DictAccessorProperty:
    read_only: Any
    name: Any
    default: Any
    load_func: Any
    dump_func: Any
    __doc__: Any
    def __init__(
        self,
        name,
        default: Any | None = ...,
        load_func: Any | None = ...,
        dump_func: Any | None = ...,
        read_only: Any | None = ...,
        doc: Any | None = ...,
    ): ...
    def __get__(self, obj, type: Any | None = ...): ...
    def __set__(self, obj, value): ...
    def __delete__(self, obj): ...

def _easteregg(app: Any | None = ...): ...
