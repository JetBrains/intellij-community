from typing import Optional, List, Union

def a(x):
    # type: (List[int]) -> List[str]
    return <warning descr="Expected type 'List[str]', got 'List[List[int]]' instead">[x]</warning>
