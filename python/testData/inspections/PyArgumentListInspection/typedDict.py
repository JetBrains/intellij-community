from typing import TypedDict


class X(TypedDict):
    x: int


x = X(x=42)
x1 = X(<warning descr="Unexpected argument">y=42</warning><warning descr="Parameter 'x' unfilled">)</warning>
