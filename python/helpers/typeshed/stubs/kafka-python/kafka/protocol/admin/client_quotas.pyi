from _typeshed import Incomplete

from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class AlterClientQuotasRequest(ApiMessage):
    class EntryData(DataContainer):
        class EntityData(DataContainer):
            entity_type: str
            entity_name: str | None
            def __init__(
                self, *args, entity_type: str = ..., entity_name: str | None = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        class OpData(DataContainer):
            key: str
            value: float
            remove: bool
            def __init__(
                self, *args, key: str = ..., value: float = ..., remove: bool = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        entity: list[EntityData]
        ops: list[OpData]
        def __init__(
            self, *args, entity: list[EntityData] = ..., ops: list[OpData] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    entries: list[EntryData]
    validate_only: bool
    def __init__(
        self, *args, entries: list[EntryData] = ..., validate_only: bool = ..., version: int | None = None, **kwargs
    ) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    API_KEY: int
    API_VERSION: int
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int
    @property
    def header(self): ...
    @classmethod
    def is_request(cls) -> bool: ...
    def expect_response(self) -> bool: ...
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

class AlterClientQuotasResponse(ApiMessage):
    class EntryData(DataContainer):
        class EntityData(DataContainer):
            entity_type: str
            entity_name: str | None
            def __init__(
                self, *args, entity_type: str = ..., entity_name: str | None = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        error_code: int
        error_message: str | None
        entity: list[EntityData]
        def __init__(
            self,
            *args,
            error_code: int = ...,
            error_message: str | None = ...,
            entity: list[EntityData] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    entries: list[EntryData]
    def __init__(
        self, *args, throttle_time_ms: int = ..., entries: list[EntryData] = ..., version: int | None = None, **kwargs
    ) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    API_KEY: int
    API_VERSION: int
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int
    @property
    def header(self): ...
    @classmethod
    def is_request(cls) -> bool: ...
    def expect_response(self) -> bool: ...
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

class DescribeClientQuotasRequest(ApiMessage):
    class ComponentData(DataContainer):
        entity_type: str
        match_type: int
        match: str | None
        def __init__(
            self,
            *args,
            entity_type: str = ...,
            match_type: int = ...,
            match: str | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    components: list[ComponentData]
    strict: bool
    def __init__(
        self, *args, components: list[ComponentData] = ..., strict: bool = ..., version: int | None = None, **kwargs
    ) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    API_KEY: int
    API_VERSION: int
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int
    @property
    def header(self): ...
    @classmethod
    def is_request(cls) -> bool: ...
    def expect_response(self) -> bool: ...
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

class DescribeClientQuotasResponse(ApiMessage):
    class EntryData(DataContainer):
        class EntityData(DataContainer):
            entity_type: str
            entity_name: str | None
            def __init__(
                self, *args, entity_type: str = ..., entity_name: str | None = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        class ValueData(DataContainer):
            key: str
            value: float
            def __init__(self, *args, key: str = ..., value: float = ..., version: int | None = None, **kwargs) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        entity: list[EntityData]
        values: list[ValueData]
        def __init__(
            self, *args, entity: list[EntityData] = ..., values: list[ValueData] = ..., version: int | None = None, **kwargs
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    error_message: str | None
    entries: list[EntryData] | None
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        error_message: str | None = ...,
        entries: list[EntryData] | None = ...,
        version: int | None = None,
        **kwargs,
    ) -> None: ...
    @property
    def version(self) -> int | None: ...
    def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...
    name: str
    type: str
    API_KEY: int
    API_VERSION: int
    valid_versions: tuple[int, int]
    min_version: int
    max_version: int
    @property
    def header(self): ...
    @classmethod
    def is_request(cls) -> bool: ...
    def expect_response(self) -> bool: ...
    def with_header(self, correlation_id: int = 0, client_id: str = "kafka-python") -> None: ...

__all__ = ["AlterClientQuotasRequest", "AlterClientQuotasResponse", "DescribeClientQuotasRequest", "DescribeClientQuotasResponse"]
