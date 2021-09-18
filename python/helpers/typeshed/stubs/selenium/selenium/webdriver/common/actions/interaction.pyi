from typing import Any

KEY: str
POINTER: str
NONE: str
SOURCE_TYPES: Any
POINTER_MOUSE: str
POINTER_TOUCH: str
POINTER_PEN: str
POINTER_KINDS: Any

class Interaction:
    PAUSE: str
    source: Any
    def __init__(self, source) -> None: ...

class Pause(Interaction):
    source: Any
    duration: Any
    def __init__(self, source, duration: int = ...) -> None: ...
    def encode(self): ...
