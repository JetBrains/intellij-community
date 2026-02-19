from typing import Any, Callable, Literal, overload


@overload
def field1(
        *,
        # default: str | None = None,
        resolver: Callable[[], Any],
        init: Literal[False] = False,
) -> Any:
    ...

@overload
def field1(
        *,
        init: Literal[True] = True,
        kw_only: bool = True,
        default: Any = None
) -> Any:
    ...

def field1(**kwargs) -> Any:
    return kwargs
