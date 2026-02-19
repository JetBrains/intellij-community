import os
import typing


class SomeClass:
    def __init__(self) -> None:
        pass

    def someMethod(self) -> tuple[str, str]:
        return "Some Method", "Some Method"


def some_function(key: str, num: int) -> str:<fold text='...'>
    """ My comment
        two lines
    """
    return "hi"</fold>
