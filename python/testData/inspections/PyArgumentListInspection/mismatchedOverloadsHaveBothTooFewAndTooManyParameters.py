from typing import overload, Optional

@overload
def mouse_event(x1: int, y1: int) -> None: ...
@overload
def mouse_event(x1: int, y1: int, x2: int, y2: int, y3: int) -> None: ...

def mouse_event(x1: int, y1: int, x2: Optional[int] = None, y2: Optional[int] = None, y3: Optional[int] = None) -> int:
    pass


mouse_event(1<warning descr="No overload of 'mouse_event' matches the arguments. Argument types: (Literal[1]). Expected one of: (x1: int, y1: int), (x1: int, y1: int, x2: int, y2: int, y3: int)">)</warning>                 # Parameters unfilled
mouse_event(1, 2)              # OK
mouse_event<warning descr="No overload of 'mouse_event' matches the arguments. Argument types: (Literal[1], Literal[2], Literal[3]). Expected one of: (x1: int, y1: int), (x1: int, y1: int, x2: int, y2: int, y3: int)">(1, 2, 3)</warning>           # OK (it shouldn't be)
mouse_event<warning descr="No overload of 'mouse_event' matches the arguments. Argument types: (Literal[1], Literal[2], Literal[3], Literal[4]). Expected one of: (x1: int, y1: int), (x1: int, y1: int, x2: int, y2: int, y3: int)">(1, 2, 3, 4)</warning>        # OK (it shouldn't be)
mouse_event(1, 2, 3, 4, 5)     # OK
mouse_event<warning descr="No overload of 'mouse_event' matches the arguments. Argument types: (Literal[1], Literal[2], Literal[3], Literal[4], Literal[5], Literal[6]). Expected one of: (x1: int, y1: int), (x1: int, y1: int, x2: int, y2: int, y3: int)">(1, 2, 3, 4, 5, 6)</warning>  # Unexpected argument(s)