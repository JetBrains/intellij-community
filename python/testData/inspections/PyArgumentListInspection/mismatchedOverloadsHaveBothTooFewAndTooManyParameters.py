from typing import overload, Optional

@overload
def mouse_event(x1: int, y1: int) -> None: ...
@overload
def mouse_event(x1: int, y1: int, x2: int, y2: int, y3: int) -> None: ...

def mouse_event(x1: int, y1: int, x2: Optional[int] = None, y2: Optional[int] = None, y3: Optional[int] = None) -> int:
    pass


mouse_event(1<warning descr="Parameter(s) unfilledPossible callees:mouse_event(x1: int, y1: int)mouse_event(x1: int, y1: int, x2: int, y2: int, y3: int)">)</warning>                 # Parameters unfilled
mouse_event(1, 2)              # OK
mouse_event<warning descr="Incorrect argument(s)Possible callees:mouse_event(x1: int, y1: int)mouse_event(x1: int, y1: int, x2: int, y2: int, y3: int)">(1, 2, 3)</warning>           # OK (it shouldn't be)
mouse_event<warning descr="Incorrect argument(s)Possible callees:mouse_event(x1: int, y1: int)mouse_event(x1: int, y1: int, x2: int, y2: int, y3: int)">(1, 2, 3, 4)</warning>        # OK (it shouldn't be)
mouse_event(1, 2, 3, 4, 5)     # OK
mouse_event<warning descr="Unexpected argument(s)Possible callees:mouse_event(x1: int, y1: int)mouse_event(x1: int, y1: int, x2: int, y2: int, y3: int)">(1, 2, 3, 4, 5, 6)</warning>  # Unexpected argument(s)