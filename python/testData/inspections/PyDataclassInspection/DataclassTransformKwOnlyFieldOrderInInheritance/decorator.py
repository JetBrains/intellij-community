import dataclasses
from typing import dataclass_transform, Callable, TypeVar

T = TypeVar("T")


def my_field(*, default=dataclasses.MISSING, kw_only=False):
    ...


class MyField:
    def __init__(self, default=dataclasses.MISSING, kw_only=False):
        ...


def my_field_kw_only_default(*, default=dataclasses.MISSING, kw_only=True):
    ...


class MyFieldKwOnlyDefault:
    def __init__(self, default=dataclasses.MISSING, kw_only=True):
        ...


@dataclass_transform(field_specifiers=(my_field, my_field_kw_only_default, MyField, MyFieldKwOnlyDefault))
def my_dataclass(**kwargs) -> Callable[[type[T]], type[T]]:
    ...


@dataclass_transform(kw_only_default=True,
                     field_specifiers=(my_field, my_field_kw_only_default, MyField, MyFieldKwOnlyDefault))
def my_dataclass_kw_only_default(**kwargs) -> Callable[[type[T]], type[T]]:
    ...
