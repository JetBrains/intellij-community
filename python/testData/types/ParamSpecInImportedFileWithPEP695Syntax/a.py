from typing import Callable

def changes_return_type_to_str[**P](x: Callable[P, int]) -> Callable[P, str]:
    ...