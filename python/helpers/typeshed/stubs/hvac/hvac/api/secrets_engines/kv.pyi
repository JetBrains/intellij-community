import logging

from hvac.api.secrets_engines import KvV1, KvV2
from hvac.api.vault_api_base import VaultApiBase

logger: logging.Logger

class Kv(VaultApiBase):
    allowed_kv_versions: list[str]
    def __init__(self, adapter, default_kv_version: str = "2") -> None: ...
    @property
    def v1(self) -> KvV1: ...
    @property
    def v2(self) -> KvV2: ...

    @property
    def default_kv_version(self) -> str: ...
    @default_kv_version.setter
    def default_kv_version(self, default_kv_version: str) -> None: ...

    def __getattr__(self, item: str): ...
