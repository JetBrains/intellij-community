from typing import TypedDict


class X(TypedDict):
    x: int


x = X(x=42)
x.clear()
x.setdefault(<warning descr="Parameter '__key' unfilled">)</warning>
x.setdefault('x', 43)

x1: X = {'x': 42}
x1.clear()
x1.setdefault(<warning descr="Parameter '__key' unfilled">)</warning>
x1.setdefault('x', 43)
