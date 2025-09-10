from . import base

spaceCharacters: str

class Filter(base.Filter):
    require_matching_tags: bool
    def __init__(self, source, require_matching_tags: bool = True) -> None: ...
    def __iter__(self): ...
