from typing import TypedDict


class X(TypedDict):
    x: int


x = X(<warning descr="Unexpected argument">42</warning><warning descr="Parameter 'x' unfilled">)</warning>
