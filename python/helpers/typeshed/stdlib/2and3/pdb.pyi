# NOTE: This stub is incomplete - only contains some global functions

from typing import Any, Dict, Optional

def run(statement: str,
        globals: Optional[Dict[str, Any]] = None,
        locals: Optional[Dict[str, Any]] = None) -> None:
    ...

def runeval(expression: str,
            globals: Optional[Dict[str, Any]] = None,
            locals: Optional[Dict[str, Any]] = None) -> Any:
    ...

def runctx(statement: str,
        globals: Dict[str, Any],
        locals: Dict[str, Any]) -> None:
    ...

def runcall(*args: Any, **kwds: Any) -> Any:
    ...

def set_trace() -> None:
    ...

def post_mortem(t: Optional[Any] = None) -> None:
    ...

def pm() -> None:
    ...
