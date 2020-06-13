
import sys
import contextvars
from typing import List, Tuple, Callable, Sequence
from . import futures

if sys.version_info >= (3, 8):
    from typing import Literal
else:
    from typing_extensions import Literal

_PENDING: Literal["PENDING"]  # undocumented
_CANCELLED: Literal["CANCELLED"]  # undocumented
_FINISHED: Literal["FINISHED"]  # undocumented

def isfuture(obj: object) -> bool: ...
def _format_callbacks(cb: Sequence[Tuple[Callable[[futures.Future], None], contextvars.Context]]) -> str: ...  # undocumented
def _future_repr_info(future: futures.Future) -> List[str]: ...  # undocumented
