from typing import Callable, ParamSpec

P = ParamSpec("P")


def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]:
    ...
