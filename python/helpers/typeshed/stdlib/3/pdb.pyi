# Stub for pdb (incomplete, only some global functions)

from typing import Any, Dict

def run(statement: str,
        globals: Dict[str, Any] = None,
        locals: Dict[str, Any] = None) -> None:
    ...

def runeval(expression: str,
            globals: Dict[str, Any] = None,
            locals: Dict[str, Any] = None) -> Any:
    ...

def runctx(statement: str,
        globals: Dict[str, Any],
        locals: Dict[str, Any]) -> None:
    ...

def runcall(*args: Any, **kwds: Any) -> Any:
    ...

def set_trace() -> None:
    ...

def post_mortem(t: Any = None) -> None:
    ...

def pm() -> None:
    ...
