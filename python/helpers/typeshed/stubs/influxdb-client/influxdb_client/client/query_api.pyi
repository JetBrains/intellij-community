from _typeshed import Incomplete, SupportsItems
from collections.abc import Callable, Generator
from typing import Any

from influxdb_client import Dialect
from influxdb_client.client._base import _BaseQueryApi
from influxdb_client.client.flux_table import CSVIterator, FluxRecord, TableList
from influxdb_client.domain.organization import Organization

class QueryOptions:
    profilers: Incomplete
    profiler_callback: Incomplete
    def __init__(
        self, profilers: list[str] | None = None, profiler_callback: Callable[..., Incomplete] | None = None
    ) -> None: ...

class QueryApi(_BaseQueryApi):
    def __init__(self, influxdb_client, query_options=...) -> None: ...
    def query_csv(
        self,
        query: str,
        org: Incomplete | None = None,
        dialect: Dialect = ...,
        params: SupportsItems[str, Incomplete] | None = None,
    ) -> CSVIterator: ...
    def query_raw(
        self, query: str, org: Incomplete | None = None, dialect=..., params: SupportsItems[str, Incomplete] | None = None
    ): ...
    def query(
        self, query: str, org: Incomplete | None = None, params: SupportsItems[str, Incomplete] | None = None
    ) -> TableList: ...
    def query_stream(
        self, query: str, org: Incomplete | None = None, params: SupportsItems[str, Incomplete] | None = None
    ) -> Generator[FluxRecord, Any, None]: ...
    def query_data_frame(
        self,
        query: str,
        org: Organization | str | None = None,
        data_frame_index: list[str] | None = None,
        params: SupportsItems[str, Incomplete] | None = None,
        use_extension_dtypes: bool = False,
    ): ...
    def query_data_frame_stream(
        self,
        query: str,
        org: Organization | str | None = None,
        data_frame_index: list[str] | None = None,
        params: SupportsItems[str, Incomplete] | None = None,
        use_extension_dtypes: bool = False,
    ): ...
    def __del__(self) -> None: ...
