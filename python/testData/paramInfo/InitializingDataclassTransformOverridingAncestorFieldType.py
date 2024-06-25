from typing import dataclass_transform, Callable, TypeVar

T = TypeVar("T")


@dataclass_transform()
def my_dataclass(**kwargs) -> Callable[[type[T]], type[T]]:
    ...


@my_dataclass()
class Super:
    super_attr: int


@my_dataclass()
class Sub(Super):
    sub_attr: int
    super_attr: str


Sub(<arg1>)