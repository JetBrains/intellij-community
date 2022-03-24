from typing import Any, Generic, TypeVar

from ..orm.query import Query
from ..orm.session import Session

_T = TypeVar("_T")

class ShardedQuery(Query[_T], Generic[_T]):
    id_chooser: Any
    query_chooser: Any
    execute_chooser: Any
    def __init__(self, *args, **kwargs) -> None: ...
    def set_shard(self, shard_id): ...

class ShardedSession(Session):
    shard_chooser: Any
    id_chooser: Any
    execute_chooser: Any
    query_chooser: Any
    def __init__(
        self, shard_chooser, id_chooser, execute_chooser: Any | None = ..., shards: Any | None = ..., query_cls=..., **kwargs
    ): ...
    def connection_callable(self, mapper: Any | None = ..., instance: Any | None = ..., shard_id: Any | None = ..., **kwargs): ...
    def get_bind(self, mapper: Any | None = ..., shard_id: Any | None = ..., instance: Any | None = ..., clause: Any | None = ..., **kw): ...  # type: ignore[override]
    def bind_shard(self, shard_id, bind) -> None: ...
