from typing import Any

TYPES_MAP: Any
REVERSE_TYPES_MAP: Any

def signature(*arguments): ...

class FunctionRegistry(type):
    def __init__(cls, name, bases, attrs) -> None: ...

class Functions:
    FUNCTION_TABLE: Any
    def call_function(self, function_name, resolved_args): ...
