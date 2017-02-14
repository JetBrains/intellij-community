from typing import TypeVar, List

T1 = TypeVar('T1', int)
T2 = TypeVar('T2', int, str)
T3 = TypeVar('T3', List[bool])

def f(p1: T1, p2: T2, p3: T3):
    pass
    
<the_ref>f()