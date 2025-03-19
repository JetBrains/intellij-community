from typing import dataclass_transform, Callable, TypeVar

T = TypeVar("T")


@dataclass_transform()
def my_dataclass(**kwargs) -> Callable[[type[T]], type[T]]:
    ...


@dataclass_transform(order_default=True)
def my_dataclass_order_default(**kwargs) -> Callable[[type[T]], type[T]]:
    ...
