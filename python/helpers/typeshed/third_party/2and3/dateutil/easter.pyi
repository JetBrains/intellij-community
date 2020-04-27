from datetime import date
import sys
if sys.version_info >= (3, 8):
    from typing import Literal
else:
    from typing_extensions import Literal

EASTER_JULIAN: Literal[1]
EASTER_ORTHODOX: Literal[2]
EASTER_WESTERN: Literal[3]

def easter(year: int, method: Literal[1, 2, 3] = ...) -> date: ...
