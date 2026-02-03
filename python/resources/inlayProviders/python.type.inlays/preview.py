from typing import reveal_type

def example(x: int, y: float)/*<# -> float #>*/:
    reveal_type(x + y)/*<# float #>*/
    return x + y

reveal_type(example(1, 2.5))/*<# float #>*/
    
    
    