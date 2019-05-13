from typing import Any
from m1 import C

def f(x: Any):
    c = C()
    <caret>expr = c.foo(x)
