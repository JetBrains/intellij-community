from typing import reveal_type


def example(x: int, y: float)/*<# -> float #>*/:
    return x + y


def gen(x: list[int])/*<# -> Generator[int, Any, str] #>*/:
    for i in x:
        yield i
    return "end"
