import enum
import sys
from typing import Any, Literal, TypeVar, overload, type_check_only

from _typeshed import ConvertibleToInt
from django.utils.functional import _StrOrPromise
from typing_extensions import deprecated

if sys.version_info >= (3, 11):
    from enum import EnumType, IntEnum, StrEnum
    from enum import property as enum_property
else:
    from enum import EnumMeta as EnumType
    from types import DynamicClassAttribute as enum_property

    class ReprEnum(enum.Enum): ...  # type: ignore[misc]
    class IntEnum(int, ReprEnum): ...  # type: ignore[misc]
    class StrEnum(str, ReprEnum): ...  # type: ignore[misc]

_Self = TypeVar("_Self", bound=ChoicesType)

class ChoicesType(EnumType):
    __empty__: _StrOrPromise
    def __new__(
        metacls: type[_Self], classname: str, bases: tuple[type, ...], classdict: enum._EnumDict, **kwds: Any
    ) -> _Self: ...
    @property
    def names(self) -> list[str]: ...
    @property
    def choices(self) -> list[tuple[Any, _StrOrPromise]]: ...
    @property
    def labels(self) -> list[_StrOrPromise]: ...
    @property
    def values(self) -> list[Any]: ...
    if sys.version_info < (3, 12):
        def __contains__(self, member: Any) -> bool: ...

@deprecated("ChoicesMeta is deprecated in favor of ChoicesType and will be removed in Django 6.0.")
class ChoicesMeta(ChoicesType): ...

class Choices(enum.Enum, metaclass=ChoicesType):  # type: ignore[misc]
    _label_: _StrOrPromise
    do_not_call_in_templates: Literal[True]

    @enum_property
    def label(self) -> _StrOrPromise: ...
    @enum_property
    def value(self) -> Any: ...

# fake, to keep simulate class properties
@type_check_only
class _IntegerChoicesType(ChoicesType):
    @property
    def choices(self) -> list[tuple[int, _StrOrPromise]]: ...
    @property
    def values(self) -> list[int]: ...

# In reality, the `__init__` overloads provided below should also support
# all the arguments of `int.__new__`/`str.__new__` (e.g. `base`, `encoding`).
# They are omitted on purpose to avoid having convoluted stubs for these enums:
class IntegerChoices(Choices, IntEnum, metaclass=_IntegerChoicesType):  # type: ignore[misc]
    @overload
    def __init__(self, x: ConvertibleToInt) -> None: ...
    @overload
    def __init__(self, x: ConvertibleToInt, label: _StrOrPromise) -> None: ...
    @enum_property
    def value(self) -> int: ...

# fake, to keep simulate class properties
@type_check_only
class _TextChoicesType(ChoicesType):
    @property
    def choices(self) -> list[tuple[str, _StrOrPromise]]: ...
    @property
    def values(self) -> list[str]: ...

class TextChoices(Choices, StrEnum, metaclass=_TextChoicesType):  # type: ignore[misc]
    @overload
    def __init__(self, object: str) -> None: ...
    @overload
    def __init__(self, object: str, label: _StrOrPromise) -> None: ...
    @enum_property
    def value(self) -> str: ...

__all__ = ["Choices", "IntegerChoices", "TextChoices"]
