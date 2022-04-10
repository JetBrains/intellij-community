from typing import Any

from ..plugin import Error as Error, TConfig as TConfig, Visitor as Visitor

def assert_error(
    visitor_cls: type[Visitor[TConfig]], src: str, expected: type[Error], config: TConfig | None = ..., **kwargs: Any
) -> None: ...
def assert_not_error(visitor_cls: type[Visitor[TConfig]], src: str, config: TConfig | None = ...) -> None: ...
