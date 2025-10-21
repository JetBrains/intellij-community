from _typeshed import Incomplete
from collections.abc import Iterable, Mapping
from typing import Final, Literal, TypeVar, overload

from .healthcheck import Healthcheck

_T = TypeVar("_T")

class TaskTemplate(dict[str, Incomplete]):
    def __init__(
        self,
        container_spec: ContainerSpec,
        resources: Resources | None = None,
        restart_policy: RestartPolicy | None = None,
        placement: Placement | list[str] | None = None,
        log_driver: DriverConfig | None = None,
        networks: Iterable[str | NetworkAttachmentConfig] | None = None,
        force_update: int | None = None,
    ) -> None: ...
    @property
    def container_spec(self) -> ContainerSpec: ...
    @property
    def resources(self) -> Resources: ...
    @property
    def restart_policy(self) -> RestartPolicy: ...
    @property
    def placement(self) -> Placement: ...

class ContainerSpec(dict[str, Incomplete]):
    def __init__(
        self,
        image: str,
        command: str | list[str] | None = None,
        args: list[str] | None = None,
        hostname: str | None = None,
        env: dict[str, Incomplete] | list[str] | None = None,
        workdir: str | None = None,
        user: str | None = None,
        labels: dict[Incomplete, Incomplete] | None = None,
        mounts: Iterable[str | Mount] | None = None,
        stop_grace_period: int | None = None,
        secrets: list[SecretReference] | None = None,
        tty: bool | None = None,
        groups: list[Incomplete] | None = None,
        open_stdin: bool | None = None,
        read_only: bool | None = None,
        stop_signal: str | None = None,
        healthcheck: Healthcheck | None = None,
        hosts: Mapping[str, str] | None = None,
        dns_config: DNSConfig | None = None,
        configs: list[ConfigReference] | None = None,
        privileges: Privileges | None = None,
        isolation: str | None = None,
        init: bool | None = None,
        cap_add: list[Incomplete] | None = None,
        cap_drop: list[Incomplete] | None = None,
        sysctls: dict[str, Incomplete] | None = None,
    ) -> None: ...

class Mount(dict[str, Incomplete]):
    def __init__(
        self,
        target: str,
        source: str,
        type: Literal["bind", "volume", "tmpfs", "npipe"] = "volume",
        read_only: bool = False,
        consistency: Literal["default", "consistent", "cached", "delegated"] | None = None,
        propagation: str | None = None,
        no_copy: bool = False,
        labels: dict[Incomplete, Incomplete] | None = None,
        driver_config: DriverConfig | None = None,
        tmpfs_size: int | str | None = None,
        tmpfs_mode: int | None = None,
    ) -> None: ...
    @classmethod
    def parse_mount_string(cls, string: str) -> Mount: ...

class Resources(dict[str, Incomplete]):
    def __init__(
        self,
        cpu_limit: int | None = None,
        mem_limit: int | None = None,
        cpu_reservation: int | None = None,
        mem_reservation: int | None = None,
        generic_resources: dict[str, Incomplete] | list[str] | None = None,
    ) -> None: ...

class UpdateConfig(dict[str, Incomplete]):
    def __init__(
        self,
        parallelism: int = 0,
        delay: int | None = None,
        failure_action: Literal["pause", "continue", "rollback"] = "continue",
        monitor: int | None = None,
        max_failure_ratio: float | None = None,
        order: Literal["start-first", "stop-first"] | None = None,
    ) -> None: ...

class RollbackConfig(UpdateConfig): ...

class RestartConditionTypesEnum:
    NONE: Final = "none"
    ON_FAILURE: Final = "on-failure"
    ANY: Final = "any"

class RestartPolicy(dict[str, Incomplete]):
    condition_types: type[RestartConditionTypesEnum]
    def __init__(
        self, condition: Literal["none", "on-failure", "any"] = "none", delay: int = 0, max_attempts: int = 0, window: int = 0
    ) -> None: ...

class DriverConfig(dict[str, Incomplete]):
    def __init__(self, name: str, options: dict[Incomplete, Incomplete] | None = None) -> None: ...

class EndpointSpec(dict[str, Incomplete]):
    def __init__(
        self, mode: str | None = None, ports: Mapping[str, str | tuple[str | None, ...]] | list[dict[str, str]] | None = None
    ) -> None: ...

@overload
def convert_service_ports(ports: list[_T]) -> list[_T]: ...
@overload
def convert_service_ports(ports: Mapping[str, str | tuple[str | None, ...]]) -> list[dict[str, str]]: ...

class ServiceMode(dict[str, Incomplete]):
    mode: Literal["replicated", "global", "ReplicatedJob", "GlobalJob"]
    def __init__(
        self,
        mode: Literal["replicated", "global", "replicated-job", "global-job"],
        replicas: int | None = None,
        concurrency: int | None = None,
    ) -> None: ...
    @property
    def replicas(self) -> int | None: ...

class SecretReference(dict[str, Incomplete]):
    def __init__(
        self,
        secret_id: str,
        secret_name: str,
        filename: str | None = None,
        uid: str | None = None,
        gid: str | None = None,
        mode: int = 292,
    ) -> None: ...

class ConfigReference(dict[str, Incomplete]):
    def __init__(
        self,
        config_id: str,
        config_name: str,
        filename: str | None = None,
        uid: str | None = None,
        gid: str | None = None,
        mode: int = 292,
    ) -> None: ...

class Placement(dict[str, Incomplete]):
    def __init__(
        self,
        constraints: list[str] | None = None,
        preferences: Iterable[tuple[str, str] | PlacementPreference] | None = None,
        platforms: Iterable[tuple[str, str]] | None = None,
        maxreplicas: int | None = None,
    ) -> None: ...

class PlacementPreference(dict[str, Incomplete]):
    def __init__(self, strategy: Literal["spread"], descriptor: str) -> None: ...

class DNSConfig(dict[str, Incomplete]):
    def __init__(
        self, nameservers: list[str] | None = None, search: list[str] | None = None, options: list[str] | None = None
    ) -> None: ...

class Privileges(dict[str, Incomplete]):
    def __init__(
        self,
        credentialspec_file: str | None = None,
        credentialspec_registry: str | None = None,
        selinux_disable: bool | None = None,
        selinux_user: str | None = None,
        selinux_role: str | None = None,
        selinux_type: str | None = None,
        selinux_level: str | None = None,
    ) -> None: ...

class NetworkAttachmentConfig(dict[str, Incomplete]):
    def __init__(self, target: str, aliases: list[str] | None = None, options: dict[str, Incomplete] | None = None) -> None: ...
