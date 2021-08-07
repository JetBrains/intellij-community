from typing import Union


def foo() -> Union[int, str, list[dict[int, str]], set[list[int]], dict[str, int]]:
    return 42