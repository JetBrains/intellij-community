from typing import Union


def foo(path: Union[bytes, str]) -> None:
    path.replace("/", "\\")