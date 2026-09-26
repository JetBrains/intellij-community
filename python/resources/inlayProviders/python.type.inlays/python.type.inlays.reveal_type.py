from typing import reveal_type


def example(x: int, y: float):
    reveal_type(x + y)/*<# float #>*/
    return x + y


reveal_type(example(1, 2.5))/*<# float #>*/
