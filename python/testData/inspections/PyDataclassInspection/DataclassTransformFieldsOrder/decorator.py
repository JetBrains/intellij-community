from typing import dataclass_transform, Callable, TypeVar

T = TypeVar("T")


def my_field(**kwargs):
    ...


class MyField:
    def __init__(self, **kwargs):
        ...

class registry:
    @dataclass_transform(field_specifiers=(my_field, MyField,))
    def mapped_as_dataclass(**kwargs) -> Callable[[type[T]], type[T]]:
        ...



@dataclass_transform(field_specifiers=(my_field, MyField,))
def my_dataclass(**kwargs) -> Callable[[type[T]], type[T]]:
    ...
