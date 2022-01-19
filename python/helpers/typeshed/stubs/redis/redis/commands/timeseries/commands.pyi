from typing import Any
from typing_extensions import Literal

ADD_CMD: Literal["TS.ADD"]
ALTER_CMD: Literal["TS.ALTER"]
CREATERULE_CMD: Literal["TS.CREATERULE"]
CREATE_CMD: Literal["TS.CREATE"]
DECRBY_CMD: Literal["TS.DECRBY"]
DELETERULE_CMD: Literal["TS.DELETERULE"]
DEL_CMD: Literal["TS.DEL"]
GET_CMD: Literal["TS.GET"]
INCRBY_CMD: Literal["TS.INCRBY"]
INFO_CMD: Literal["TS.INFO"]
MADD_CMD: Literal["TS.MADD"]
MGET_CMD: Literal["TS.MGET"]
MRANGE_CMD: Literal["TS.MRANGE"]
MREVRANGE_CMD: Literal["TS.MREVRANGE"]
QUERYINDEX_CMD: Literal["TS.QUERYINDEX"]
RANGE_CMD: Literal["TS.RANGE"]
REVRANGE_CMD: Literal["TS.REVRANGE"]

class TimeSeriesCommands:
    def create(self, key, **kwargs): ...
    def alter(self, key, **kwargs): ...
    def add(self, key, timestamp, value, **kwargs): ...
    def madd(self, ktv_tuples): ...
    def incrby(self, key, value, **kwargs): ...
    def decrby(self, key, value, **kwargs): ...
    def delete(self, key, from_time, to_time): ...
    def createrule(self, source_key, dest_key, aggregation_type, bucket_size_msec): ...
    def deleterule(self, source_key, dest_key): ...
    def range(
        self,
        key,
        from_time,
        to_time,
        count: Any | None = ...,
        aggregation_type: Any | None = ...,
        bucket_size_msec: int = ...,
        filter_by_ts: Any | None = ...,
        filter_by_min_value: Any | None = ...,
        filter_by_max_value: Any | None = ...,
        align: Any | None = ...,
    ): ...
    def revrange(
        self,
        key,
        from_time,
        to_time,
        count: Any | None = ...,
        aggregation_type: Any | None = ...,
        bucket_size_msec: int = ...,
        filter_by_ts: Any | None = ...,
        filter_by_min_value: Any | None = ...,
        filter_by_max_value: Any | None = ...,
        align: Any | None = ...,
    ): ...
    def mrange(
        self,
        from_time,
        to_time,
        filters,
        count: Any | None = ...,
        aggregation_type: Any | None = ...,
        bucket_size_msec: int = ...,
        with_labels: bool = ...,
        filter_by_ts: Any | None = ...,
        filter_by_min_value: Any | None = ...,
        filter_by_max_value: Any | None = ...,
        groupby: Any | None = ...,
        reduce: Any | None = ...,
        select_labels: Any | None = ...,
        align: Any | None = ...,
    ): ...
    def mrevrange(
        self,
        from_time,
        to_time,
        filters,
        count: Any | None = ...,
        aggregation_type: Any | None = ...,
        bucket_size_msec: int = ...,
        with_labels: bool = ...,
        filter_by_ts: Any | None = ...,
        filter_by_min_value: Any | None = ...,
        filter_by_max_value: Any | None = ...,
        groupby: Any | None = ...,
        reduce: Any | None = ...,
        select_labels: Any | None = ...,
        align: Any | None = ...,
    ): ...
    def get(self, key): ...
    def mget(self, filters, with_labels: bool = ...): ...
    def info(self, key): ...
    def queryindex(self, filters): ...
