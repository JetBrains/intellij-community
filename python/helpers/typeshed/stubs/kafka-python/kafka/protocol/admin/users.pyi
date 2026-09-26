from _typeshed import Incomplete

from kafka.protocol.api_data import ApiData
from kafka.protocol.api_message import ApiMessage
from kafka.protocol.data_container import DataContainer

class AlterUserScramCredentialsRequest(ApiMessage):
    class ScramCredentialDeletion(DataContainer):
        name: str
        mechanism: int
        def __init__(self, *args, name: str = ..., mechanism: int = ..., version: int | None = None, **kwargs) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    class ScramCredentialUpsertion(DataContainer):
        name: str
        mechanism: int
        iterations: int
        salt: bytes | ApiData
        salted_password: bytes | ApiData
        def __init__(
            self,
            *args,
            name: str = ...,
            mechanism: int = ...,
            iterations: int = ...,
            salt: bytes | ApiData = ...,
            salted_password: bytes | ApiData = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    deletions: list[ScramCredentialDeletion]
    upsertions: list[ScramCredentialUpsertion]
    def __init__(
        self,
        *args,
        deletions: list[ScramCredentialDeletion] = ...,
        upsertions: list[ScramCredentialUpsertion] = ...,
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

class AlterUserScramCredentialsResponse(ApiMessage):
    class AlterUserScramCredentialsResult(DataContainer):
        user: str
        error_code: int
        error_message: str | None
        def __init__(
            self,
            *args,
            user: str = ...,
            error_code: int = ...,
            error_message: str | None = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    results: list[AlterUserScramCredentialsResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        results: list[AlterUserScramCredentialsResult] = ...,
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

class DescribeUserScramCredentialsRequest(ApiMessage):
    class UserName(DataContainer):
        name: str
        def __init__(self, *args, name: str = ..., version: int | None = None, **kwargs) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    users: list[UserName] | None
    def __init__(self, *args, users: list[UserName] | None = ..., version: int | None = None, **kwargs) -> None: ...
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

class DescribeUserScramCredentialsResponse(ApiMessage):
    class DescribeUserScramCredentialsResult(DataContainer):
        class CredentialInfo(DataContainer):
            mechanism: int
            iterations: int
            def __init__(
                self, *args, mechanism: int = ..., iterations: int = ..., version: int | None = None, **kwargs
            ) -> None: ...
            @property
            def version(self) -> int | None: ...
            def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

        user: str
        error_code: int
        error_message: str | None
        credential_infos: list[CredentialInfo]
        def __init__(
            self,
            *args,
            user: str = ...,
            error_code: int = ...,
            error_message: str | None = ...,
            credential_infos: list[CredentialInfo] = ...,
            version: int | None = None,
            **kwargs,
        ) -> None: ...
        @property
        def version(self) -> int | None: ...
        def to_dict(self, meta: bool = False, json: bool = True) -> dict[Incomplete, Incomplete]: ...

    throttle_time_ms: int
    error_code: int
    error_message: str | None
    results: list[DescribeUserScramCredentialsResult]
    def __init__(
        self,
        *args,
        throttle_time_ms: int = ...,
        error_code: int = ...,
        error_message: str | None = ...,
        results: list[DescribeUserScramCredentialsResult] = ...,
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

__all__ = [
    "AlterUserScramCredentialsRequest",
    "AlterUserScramCredentialsResponse",
    "DescribeUserScramCredentialsRequest",
    "DescribeUserScramCredentialsResponse",
]
