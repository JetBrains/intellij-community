from typing import Any

from django.contrib.gis.db.backends.base.adapter import WKTAdapter

class OracleSpatialAdapter(WKTAdapter):
    input_size: Any
    wkt: Any
    srid: Any
    def __init__(self, geom: Any) -> None: ...
