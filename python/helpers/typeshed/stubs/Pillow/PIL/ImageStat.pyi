from _typeshed import Incomplete

class Stat:
    h: Incomplete
    bands: Incomplete
    def __init__(self, image_or_list, mask: Incomplete | None = None) -> None: ...
    def __getattr__(self, id: str): ...

Global = Stat
