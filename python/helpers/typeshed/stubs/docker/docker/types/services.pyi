from _typeshed import Incomplete

class TaskTemplate(dict[str, Incomplete]):
    def __init__(
        self,
        container_spec,
        resources: Incomplete | None = None,
        restart_policy: Incomplete | None = None,
        placement: Incomplete | None = None,
        log_driver: Incomplete | None = None,
        networks: Incomplete | None = None,
        force_update: Incomplete | None = None,
    ) -> None: ...
    @property
    def container_spec(self): ...
    @property
    def resources(self): ...
    @property
    def restart_policy(self): ...
    @property
    def placement(self): ...

class ContainerSpec(dict[str, Incomplete]):
    def __init__(
        self,
        image,
        command: Incomplete | None = None,
        args: Incomplete | None = None,
        hostname: Incomplete | None = None,
        env: Incomplete | None = None,
        workdir: Incomplete | None = None,
        user: Incomplete | None = None,
        labels: Incomplete | None = None,
        mounts: Incomplete | None = None,
        stop_grace_period: Incomplete | None = None,
        secrets: Incomplete | None = None,
        tty: Incomplete | None = None,
        groups: Incomplete | None = None,
        open_stdin: Incomplete | None = None,
        read_only: Incomplete | None = None,
        stop_signal: Incomplete | None = None,
        healthcheck: Incomplete | None = None,
        hosts: Incomplete | None = None,
        dns_config: Incomplete | None = None,
        configs: Incomplete | None = None,
        privileges: Incomplete | None = None,
        isolation: Incomplete | None = None,
        init: Incomplete | None = None,
        cap_add: Incomplete | None = None,
        cap_drop: Incomplete | None = None,
        sysctls: Incomplete | None = None,
    ) -> None: ...

class Mount(dict[str, Incomplete]):
    def __init__(
        self,
        target,
        source,
        type: str = "volume",
        read_only: bool = False,
        consistency: Incomplete | None = None,
        propagation: Incomplete | None = None,
        no_copy: bool = False,
        labels: Incomplete | None = None,
        driver_config: Incomplete | None = None,
        tmpfs_size: Incomplete | None = None,
        tmpfs_mode: Incomplete | None = None,
    ) -> None: ...
    @classmethod
    def parse_mount_string(cls, string): ...

class Resources(dict[str, Incomplete]):
    def __init__(
        self,
        cpu_limit: Incomplete | None = None,
        mem_limit: Incomplete | None = None,
        cpu_reservation: Incomplete | None = None,
        mem_reservation: Incomplete | None = None,
        generic_resources: Incomplete | None = None,
    ) -> None: ...

class UpdateConfig(dict[str, Incomplete]):
    def __init__(
        self,
        parallelism: int = 0,
        delay: Incomplete | None = None,
        failure_action: str = "continue",
        monitor: Incomplete | None = None,
        max_failure_ratio: Incomplete | None = None,
        order: Incomplete | None = None,
    ) -> None: ...

class RollbackConfig(UpdateConfig): ...

class RestartConditionTypesEnum:
    NONE: Incomplete
    ON_FAILURE: Incomplete
    ANY: Incomplete

class RestartPolicy(dict[str, Incomplete]):
    condition_types: type[RestartConditionTypesEnum]
    def __init__(self, condition="none", delay: int = 0, max_attempts: int = 0, window: int = 0) -> None: ...

class DriverConfig(dict[str, Incomplete]):
    def __init__(self, name, options: Incomplete | None = None) -> None: ...

class EndpointSpec(dict[str, Incomplete]):
    def __init__(self, mode: Incomplete | None = None, ports: Incomplete | None = None) -> None: ...

def convert_service_ports(ports): ...

class ServiceMode(dict[str, Incomplete]):
    mode: Incomplete
    def __init__(self, mode, replicas: Incomplete | None = None, concurrency: Incomplete | None = None) -> None: ...
    @property
    def replicas(self): ...

class SecretReference(dict[str, Incomplete]):
    def __init__(
        self,
        secret_id,
        secret_name,
        filename: Incomplete | None = None,
        uid: Incomplete | None = None,
        gid: Incomplete | None = None,
        mode: int = 292,
    ) -> None: ...

class ConfigReference(dict[str, Incomplete]):
    def __init__(
        self,
        config_id,
        config_name,
        filename: Incomplete | None = None,
        uid: Incomplete | None = None,
        gid: Incomplete | None = None,
        mode: int = 292,
    ) -> None: ...

class Placement(dict[str, Incomplete]):
    def __init__(
        self,
        constraints: Incomplete | None = None,
        preferences: Incomplete | None = None,
        platforms: Incomplete | None = None,
        maxreplicas: Incomplete | None = None,
    ) -> None: ...

class PlacementPreference(dict[str, Incomplete]):
    def __init__(self, strategy, descriptor) -> None: ...

class DNSConfig(dict[str, Incomplete]):
    def __init__(
        self, nameservers: Incomplete | None = None, search: Incomplete | None = None, options: Incomplete | None = None
    ) -> None: ...

class Privileges(dict[str, Incomplete]):
    def __init__(
        self,
        credentialspec_file: Incomplete | None = None,
        credentialspec_registry: Incomplete | None = None,
        selinux_disable: Incomplete | None = None,
        selinux_user: Incomplete | None = None,
        selinux_role: Incomplete | None = None,
        selinux_type: Incomplete | None = None,
        selinux_level: Incomplete | None = None,
    ) -> None: ...

class NetworkAttachmentConfig(dict[str, Incomplete]):
    def __init__(self, target, aliases: Incomplete | None = None, options: Incomplete | None = None) -> None: ...
