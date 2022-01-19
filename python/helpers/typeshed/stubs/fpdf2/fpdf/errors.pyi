from typing import Any

class FPDFException(Exception): ...

class FPDFPageFormatException(FPDFException):
    argument: Any
    unknown: Any
    one: Any
    def __init__(self, argument, unknown: bool = ..., one: bool = ...) -> None: ...
