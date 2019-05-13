from typing import Any
from m1 import C

def f(x: list):
c = C()
<caret>expr = c.foo(non_existing=0)
