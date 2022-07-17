from typing import Any

vmlns: str
officens: str
excelns: str

class ShapeWriter:
    vml: Any
    vml_path: Any
    comments: Any
    def __init__(self, comments) -> None: ...
    def add_comment_shapetype(self, root) -> None: ...
    def add_comment_shape(self, root, idx, coord, height, width) -> None: ...
    def write(self, root): ...
