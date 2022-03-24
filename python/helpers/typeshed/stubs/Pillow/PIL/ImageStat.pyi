from typing import Any

class Stat:
    h: Any
    bands: Any
    def __init__(self, image_or_list, mask: Any | None = ...) -> None: ...
    def __getattr__(self, id): ...

Global = Stat
