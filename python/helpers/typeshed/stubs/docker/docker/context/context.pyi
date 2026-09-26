from typing import TypedDict, type_check_only

from docker.tls import TLSConfig as _TLSConfig

@type_check_only
class _StorageData(TypedDict):
    MetadataPath: str
    TLSPath: str

@type_check_only
class _Storage(TypedDict):
    Storage: _StorageData

@type_check_only
class _Endpoint(TypedDict, total=False):
    Host: str
    SkipTLSVerify: bool
    DefaultNamespace: str

@type_check_only
class _TLSMaterial(TypedDict):
    TLSMaterial: dict[str, list[str]]

@type_check_only
class _MetaMetaData(TypedDict, total=False):
    StackOrchestrator: str

@type_check_only
class _Metadata(TypedDict):
    Name: str
    Metadata: _MetaMetaData
    Endpoints: dict[str, _Endpoint]

@type_check_only
class _Context(_Metadata, _TLSMaterial, _Storage): ...

class Context:
    name: str
    context_type: str | None
    orchestrator: str | None
    endpoints: dict[str, _Endpoint]
    tls_cfg: dict[str, _TLSConfig]
    meta_path: str
    tls_path: str
    def __init__(
        self,
        name: str,
        orchestrator: str | None = None,
        host: str | None = None,
        endpoints: dict[str, _Endpoint] | None = None,
        tls: bool = False,
    ) -> None: ...
    def set_endpoint(
        self,
        name: str = "docker",
        host: str | None = None,
        tls_cfg: _TLSConfig | None = None,
        skip_tls_verify: bool = False,
        def_namespace: str | None = None,
    ) -> None: ...
    def inspect(self) -> _Context: ...
    @classmethod
    def load_context(cls, name: str) -> Context | None: ...
    def save(self) -> None: ...
    def remove(self) -> None: ...
    def __call__(self) -> _Context: ...
    def is_docker_host(self) -> bool: ...
    @property
    def Name(self) -> str: ...
    @property
    def Host(self) -> str | None: ...
    @property
    def Orchestrator(self) -> str: ...
    @property
    def Metadata(self) -> _Metadata: ...
    @property
    def TLSConfig(self) -> _TLSConfig: ...
    @property
    def TLSMaterial(self) -> _TLSMaterial: ...
    @property
    def Storage(self) -> _Storage: ...
