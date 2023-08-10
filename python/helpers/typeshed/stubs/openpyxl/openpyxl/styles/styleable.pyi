from typing import Any
from warnings import warn as warn

class StyleDescriptor:
    collection: Any
    key: Any
    def __init__(self, collection, key) -> None: ...
    def __set__(self, instance, value) -> None: ...
    def __get__(self, instance, cls): ...

class NumberFormatDescriptor:
    key: str
    collection: str
    def __set__(self, instance, value) -> None: ...
    def __get__(self, instance, cls): ...

class NamedStyleDescriptor:
    key: str
    collection: str
    def __set__(self, instance, value) -> None: ...
    def __get__(self, instance, cls): ...

class StyleArrayDescriptor:
    key: Any
    def __init__(self, key) -> None: ...
    def __set__(self, instance, value) -> None: ...
    def __get__(self, instance, cls): ...

class StyleableObject:
    font: Any
    fill: Any
    border: Any
    number_format: Any
    protection: Any
    alignment: Any
    style: Any
    quotePrefix: Any
    pivotButton: Any
    parent: Any
    def __init__(self, sheet, style_array: Any | None = ...) -> None: ...
    @property
    def style_id(self): ...
    @property
    def has_style(self): ...
