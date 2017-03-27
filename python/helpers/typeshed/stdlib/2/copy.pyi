# Stubs for copy

# NOTE: These are incomplete!

from typing import TypeVar, Dict, Any

_T = TypeVar('_T')

def deepcopy(x: _T, memo: Dict[Any, Any] = ...) -> _T: ...
def copy(x: _T) -> _T: ...
