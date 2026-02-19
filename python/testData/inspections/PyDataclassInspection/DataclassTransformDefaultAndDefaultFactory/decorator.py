from typing import dataclass_transform, Callable, TypeVar

T = TypeVar("T")


def my_field(**kwargs):
    ...


@dataclass_transform(field_specifiers=(my_field,))
def my_dataclass(**kwargs) -> Callable[[type[T]], type[T]]:
    ...
