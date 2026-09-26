import logging
from typing import Any, Final

log: Final[logging.Logger]

def parse_bool(v: bool | str | int) -> bool: ...

class Session:
    @classmethod
    def hascreds(cls, config: dict[str, Any]) -> bool: ...
    def get_credential_options(self) -> dict[str, str]: ...
    # `session` is a foreign session object (e.g. boto3.session.Session,
    # google.auth.credentials.Credentials); the runtime dispatches by isinstance.
    @staticmethod
    def from_foreign_session(session: Any, cls: type[Session] | None = None) -> Session: ...
    @staticmethod
    def cls_from_path(path: str) -> type[Session]: ...
    # Forwarded to the resolved session class' __init__; see its signature.
    @staticmethod
    def from_path(path: str, *args: Any, **kwargs: Any) -> Session: ...
    @staticmethod
    def aws_or_dummy(*args: Any, **kwargs: Any) -> Session: ...
    @staticmethod
    def from_environ(*args: Any, **kwargs: Any) -> Session: ...

class DummySession(Session):
    credentials: dict[str, str]
    # Accepts and ignores any args (no credentials are configured).
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    @classmethod
    def hascreds(cls, config: dict[str, Any]) -> bool: ...
    def get_credential_options(self) -> dict[str, str]: ...

class AWSSession(Session):
    requester_pays: bool
    unsigned: bool
    endpoint_url: str | None
    def __init__(
        self,
        # A `boto3.session.Session` instance, or None to construct one from the other kwargs.
        session: Any | None = None,
        aws_unsigned: bool | None = None,
        aws_access_key_id: str | None = None,
        aws_secret_access_key: str | None = None,
        aws_session_token: str | None = None,
        region_name: str | None = None,
        profile_name: str | None = None,
        endpoint_url: str | None = None,
        requester_pays: bool = False,
    ) -> None: ...
    @classmethod
    def hascreds(cls, config: dict[str, Any]) -> bool: ...
    @property
    def credentials(self) -> dict[str, str]: ...
    def get_credential_options(self) -> dict[str, str]: ...

class OSSSession(Session):
    def __init__(
        self, oss_access_key_id: str | None = None, oss_secret_access_key: str | None = None, oss_endpoint: str | None = None
    ) -> None: ...
    @classmethod
    def hascreds(cls, config: dict[str, Any]) -> bool: ...
    @property
    def credentials(self) -> dict[str, str]: ...
    def get_credential_options(self) -> dict[str, str]: ...

class GSSession(Session):
    def __init__(self, google_application_credentials: str | None = None) -> None: ...
    @classmethod
    def hascreds(cls, config: dict[str, Any]) -> bool: ...
    @property
    def credentials(self) -> dict[str, str]: ...
    def get_credential_options(self) -> dict[str, str]: ...

class SwiftSession(Session):
    def __init__(
        self,
        # A `swiftclient.Connection` instance, or None to construct one from the other kwargs.
        session: Any | None = None,
        swift_storage_url: str | None = None,
        swift_auth_token: str | None = None,
        swift_auth_v1_url: str | None = None,
        swift_user: str | None = None,
        swift_key: str | None = None,
    ) -> None: ...
    @classmethod
    def hascreds(cls, config: dict[str, Any]) -> bool: ...
    @property
    def credentials(self) -> dict[str, str]: ...
    def get_credential_options(self) -> dict[str, str]: ...

class AzureSession(Session):
    unsigned: bool
    storage_account: str | None
    def __init__(
        self,
        azure_storage_connection_string: str | None = None,
        azure_storage_account: str | None = None,
        azure_storage_access_token: str | None = None,
        azure_storage_access_key: str | None = None,
        azure_storage_sas_token: str | None = None,
        azure_unsigned: bool = False,
        azure_tenant_id: str | None = None,
        azure_client_id: str | None = None,
        azure_federated_token_file: str | None = None,
        azure_authority_host: str | None = None,
    ) -> None: ...
    @classmethod
    def hascreds(cls, config: dict[str, Any]) -> bool: ...
    @property
    def credentials(self) -> dict[str, str]: ...
    def get_credential_options(self) -> dict[str, str]: ...
