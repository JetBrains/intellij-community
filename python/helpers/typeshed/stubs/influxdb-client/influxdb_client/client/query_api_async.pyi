from _typeshed import Incomplete, SupportsItems
from collections.abc import AsyncGenerator

from influxdb_client.client._base import _BaseQueryApi
from influxdb_client.client.flux_table import FluxRecord, TableList
from influxdb_client.domain.dialect import Dialect
from influxdb_client.domain.organization import Organization

class QueryApiAsync(_BaseQueryApi):
    def __init__(self, influxdb_client, query_options=...) -> None: ...
    async def query(
        self, query: str, org: Incomplete | None = None, params: SupportsItems[str, Incomplete] | None = None
    ) -> TableList: ...
    async def query_stream(
        self, query: str, org: Incomplete | None = None, params: SupportsItems[str, Incomplete] | None = None
    ) -> AsyncGenerator[FluxRecord, None]: ...
    async def query_data_frame(
        self,
        query: str,
        org: str | Organization | None = None,
        data_frame_index: list[str] | None = None,
        params: SupportsItems[str, Incomplete] | None = None,
        use_extension_dtypes: bool = False,
    ): ...
    async def query_data_frame_stream(
        self,
        query: str,
        org: str | Organization | None = None,
        data_frame_index: list[str] | None = None,
        params: SupportsItems[str, Incomplete] | None = None,
        use_extension_dtypes: bool = False,
    ): ...
    async def query_raw(
        self,
        query: str,
        org: str | Organization | None = None,
        dialect: Dialect = ...,
        params: SupportsItems[str, Incomplete] | None = None,
    ) -> str: ...
