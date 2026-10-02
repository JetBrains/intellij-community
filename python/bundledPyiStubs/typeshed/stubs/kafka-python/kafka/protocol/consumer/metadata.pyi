from _typeshed import Incomplete
from typing import Final

from kafka.protocol.api_data import ApiData
from kafka.protocol.data_container import DataContainer

ConsumerProtocolType: Final = "consumer"

class ConsumerProtocolSubscription(ApiData):
    class TopicPartition(DataContainer):
        topic: str
        partitions: list[int]
        def __init__(
            self, *args, topic: str = ..., partitions: list[int] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    topics: list[str]
    user_data: bytes | ApiData | None
    owned_partitions: list[TopicPartition]
    generation_id: int
    rack_id: str | None
    def __init__(
        self,
        *args,
        topics: list[str] = ...,
        user_data: bytes | ApiData | None = ...,
        owned_partitions: list[TopicPartition] = ...,
        generation_id: int = ...,
        rack_id: str | None = ...,
        version: int | None = None,
        **kwargs,
    ) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int

class ConsumerProtocolAssignment(ApiData):
    class TopicPartition(DataContainer):
        topic: str
        partitions: list[int]
        def __init__(
            self, *args, topic: str = ..., partitions: list[int] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    assigned_partitions: list[TopicPartition]
    user_data: bytes | ApiData | None
    def __init__(
        self,
        *args,
        assigned_partitions: list[TopicPartition] = ...,
        user_data: bytes | ApiData | None = ...,
        version: int | None = None,
        **kwargs,
    ) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int

    @property
    def assignment(self) -> list[TopicPartition]: ...
    @assignment.setter
    def assignment(self, value: list[TopicPartition]) -> None: ...

    def partitions(self) -> list[TopicPartition]: ...

__all__ = ["ConsumerProtocolSubscription", "ConsumerProtocolAssignment", "ConsumerProtocolType"]
