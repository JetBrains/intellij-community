from functools import lru_cache
from typing import Any

from django.db.backends.postgresql.base import DatabaseWrapper as Psycopg2DatabaseWrapper
from psycopg import BaseConnection
from psycopg.adapt import Dumper
from psycopg.pq import Format
from typing_extensions import override

class BaseBinaryDumper(Dumper):
    format: Format
    @override
    def dump(self, obj: Any) -> bytes: ...

class BaseTextDumper(Dumper):
    @override
    def dump(self, obj: Any) -> bytes: ...

class DatabaseWrapper(Psycopg2DatabaseWrapper):
    SchemaEditorClass: Any
    features: Any
    ops: Any
    introspection: Any
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    @override
    def prepare_database(self) -> None: ...
    def register_geometry_adapters(self, pg_connection: BaseConnection[bytes], clear_caches: bool = False) -> None: ...

class GeometryType: ...
class GeographyType: ...
class RasterType: ...

@lru_cache
def postgis_adapters(
    geo_oid: int | None,
    geog_oid: int | None,
    raster_oid: int | None,
) -> tuple[type[Dumper], type[Dumper]]: ...
