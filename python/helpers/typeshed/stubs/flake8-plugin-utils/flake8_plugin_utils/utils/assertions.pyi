from typing import Any, Type

from ..plugin import Error as Error, TConfig as TConfig, Visitor as Visitor

def assert_error(
    visitor_cls: Type[Visitor[TConfig]], src: str, expected: Type[Error], config: TConfig | None = ..., **kwargs: Any
) -> None: ...
def assert_not_error(visitor_cls: Type[Visitor[TConfig]], src: str, config: TConfig | None = ...) -> None: ...
