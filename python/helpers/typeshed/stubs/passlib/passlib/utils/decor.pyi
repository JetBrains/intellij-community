from typing import Any

class classproperty:
    im_func: Any
    def __init__(self, func) -> None: ...
    def __get__(self, obj, cls): ...
    @property
    def __func__(self): ...

class hybrid_method:
    func: Any
    def __init__(self, func) -> None: ...
    def __get__(self, obj, cls): ...

def memoize_single_value(func): ...

class memoized_property:
    __func__: Any
    __name__: Any
    __doc__: Any
    def __init__(self, func) -> None: ...
    def __get__(self, obj, cls): ...
    def clear_cache(self, obj) -> None: ...
    def peek_cache(self, obj, default: Any | None = ...): ...

def deprecated_function(
    msg: Any | None = ...,
    deprecated: Any | None = ...,
    removed: Any | None = ...,
    updoc: bool = ...,
    replacement: Any | None = ...,
    _is_method: bool = ...,
    func_module: Any | None = ...,
): ...
def deprecated_method(
    msg: Any | None = ...,
    deprecated: Any | None = ...,
    removed: Any | None = ...,
    updoc: bool = ...,
    replacement: Any | None = ...,
): ...
