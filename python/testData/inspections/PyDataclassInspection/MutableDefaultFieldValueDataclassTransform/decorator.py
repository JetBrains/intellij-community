from typing import dataclass_transform, Callable, TypeVar

T = TypeVar("T")


def field(**kwargs):
    ...


@dataclass_transform(field_specifiers=(field,))
def my_dataclass(**kwargs) -> Callable[[type[T]], type[T]]:
    ...
