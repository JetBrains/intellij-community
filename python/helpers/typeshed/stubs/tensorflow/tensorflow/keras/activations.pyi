from _typeshed import Incomplete
from collections.abc import Callable
from typing import Any
from typing_extensions import TypeAlias

from tensorflow import Tensor

# The implementation uses isinstance so it must be dict and not any Mapping.
_Activation: TypeAlias = str | None | Callable[[Tensor], Tensor] | dict[str, Any]

def get(identifier: _Activation) -> Callable[[Tensor], Tensor]: ...
def __getattr__(name: str) -> Incomplete: ...
