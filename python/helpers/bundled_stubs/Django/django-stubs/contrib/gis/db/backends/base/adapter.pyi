from typing import Any

from typing_extensions import override

class WKTAdapter:
    wkt: Any
    srid: Any
    def __init__(self, geom: Any) -> None: ...
    @override
    def __eq__(self, other: object) -> bool: ...
    @override
    def __hash__(self) -> int: ...
