import dataclasses
from typing import dataclass_transform, Callable, TypeVar

T = TypeVar("T")


def my_field(*, default=dataclasses.MISSING, kw_only=False):
    ...


def my_filed_kw_only_default(*, default=dataclasses.MISSING, kw_only=True):
    ...


@dataclass_transform(field_specifiers=(my_field, my_filed_kw_only_default))
def my_dataclass(**kwargs) -> Callable[[type[T]], type[T]]:
    ...


@dataclass_transform(kw_only_default=True, field_specifiers=(my_field, my_filed_kw_only_default))
def my_dataclass_kw_only_default(**kwargs) -> Callable[[type[T]], type[T]]:
    ...
