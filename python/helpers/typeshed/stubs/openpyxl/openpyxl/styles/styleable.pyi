from _typeshed import Incomplete, Unused
from warnings import warn as warn

from openpyxl.descriptors import Strict
from openpyxl.descriptors.serialisable import Serialisable

class StyleDescriptor:
    collection: Incomplete
    key: Incomplete
    def __init__(self, collection, key) -> None: ...
    def __set__(self, instance: Serialisable | Strict, value) -> None: ...
    def __get__(self, instance: Serialisable | Strict, cls: Unused): ...

class NumberFormatDescriptor:
    key: str
    collection: str
    def __set__(self, instance: Serialisable | Strict, value) -> None: ...
    def __get__(self, instance: Serialisable | Strict, cls: Unused): ...

class NamedStyleDescriptor:
    key: str
    collection: str
    def __set__(self, instance: Serialisable | Strict, value) -> None: ...
    def __get__(self, instance: Serialisable | Strict, cls: Unused): ...

class StyleArrayDescriptor:
    key: Incomplete
    def __init__(self, key) -> None: ...
    def __set__(self, instance: Serialisable | Strict, value) -> None: ...
    def __get__(self, instance: Serialisable | Strict, cls: Unused): ...

class StyleableObject:
    font: Incomplete
    fill: Incomplete
    border: Incomplete
    number_format: Incomplete
    protection: Incomplete
    alignment: Incomplete
    style: Incomplete
    quotePrefix: Incomplete
    pivotButton: Incomplete
    parent: Incomplete
    def __init__(self, sheet, style_array: Incomplete | None = None) -> None: ...
    @property
    def style_id(self) -> int: ...
    @property
    def has_style(self) -> bool: ...
