from _typeshed import Incomplete
from typing import Literal

from docker._types import ContainerWeightDevice

from .base import DictType
from .services import Mount

class LogConfigTypesEnum:
    JSON: Incomplete
    SYSLOG: Incomplete
    JOURNALD: Incomplete
    GELF: Incomplete
    FLUENTD: Incomplete
    NONE: Incomplete

class LogConfig(DictType):
    types: type[LogConfigTypesEnum]
    def __init__(self, **kwargs) -> None: ...
    @property
    def type(self): ...
    @type.setter
    def type(self, value) -> None: ...
    @property
    def config(self): ...
    def set_config_value(self, key, value) -> None: ...
    def unset_config(self, key) -> None: ...

class Ulimit(DictType):
    def __init__(self, **kwargs) -> None: ...
    @property
    def name(self): ...
    @name.setter
    def name(self, value) -> None: ...
    @property
    def soft(self): ...
    @soft.setter
    def soft(self, value) -> None: ...
    @property
    def hard(self): ...
    @hard.setter
    def hard(self, value) -> None: ...

class DeviceRequest(DictType):
    def __init__(self, **kwargs) -> None: ...
    @property
    def driver(self): ...
    @driver.setter
    def driver(self, value) -> None: ...
    @property
    def count(self): ...
    @count.setter
    def count(self, value) -> None: ...
    @property
    def device_ids(self): ...
    @device_ids.setter
    def device_ids(self, value) -> None: ...
    @property
    def capabilities(self): ...
    @capabilities.setter
    def capabilities(self, value) -> None: ...
    @property
    def options(self): ...
    @options.setter
    def options(self, value) -> None: ...

class HostConfig(dict[str, Incomplete]):
    def __init__(
        self,
        version: str,
        binds: Incomplete | None = None,
        port_bindings: Incomplete | None = None,
        lxc_conf: dict[Incomplete, Incomplete] | None = None,
        publish_all_ports: bool = False,
        links: dict[str, str | None] | None = None,
        privileged: bool = False,
        dns: list[Incomplete] | None = None,
        dns_search: list[Incomplete] | None = None,
        volumes_from: list[str] | None = None,
        network_mode: str | None = None,
        restart_policy: dict[Incomplete, Incomplete] | None = None,
        cap_add: list[str] | None = None,
        cap_drop: list[str] | None = None,
        devices: list[str] | None = None,
        extra_hosts: dict[Incomplete, Incomplete] | None = None,
        read_only: bool | None = None,
        pid_mode: str | None = None,
        ipc_mode: str | None = None,
        security_opt: list[str] | None = None,
        ulimits: list[Ulimit] | None = None,
        log_config: LogConfig | None = None,
        mem_limit: str | int | None = None,
        memswap_limit: str | int | None = None,
        mem_reservation: str | int | None = None,
        kernel_memory: str | int | None = None,
        mem_swappiness: int | None = None,
        cgroup_parent: str | None = None,
        group_add: list[str | int] | None = None,
        cpu_quota: int | None = None,
        cpu_period: int | None = None,
        blkio_weight: int | None = None,
        blkio_weight_device: list[ContainerWeightDevice] | None = None,
        device_read_bps: Incomplete | None = None,
        device_write_bps: Incomplete | None = None,
        device_read_iops: Incomplete | None = None,
        device_write_iops: Incomplete | None = None,
        oom_kill_disable: bool = False,
        shm_size: str | int | None = None,
        sysctls: dict[Incomplete, Incomplete] | None = None,
        tmpfs: dict[str, str] | None = None,
        oom_score_adj: int | None = None,
        dns_opt: list[Incomplete] | None = None,
        cpu_shares: int | None = None,
        cpuset_cpus: str | None = None,
        userns_mode: str | None = None,
        uts_mode: str | None = None,
        pids_limit: int | None = None,
        isolation: str | None = None,
        auto_remove: bool = False,
        storage_opt: dict[Incomplete, Incomplete] | None = None,
        init: bool | None = None,
        init_path: str | None = None,
        volume_driver: str | None = None,
        cpu_count: int | None = None,
        cpu_percent: int | None = None,
        nano_cpus: int | None = None,
        cpuset_mems: str | None = None,
        runtime: str | None = None,
        mounts: list[Mount] | None = None,
        cpu_rt_period: int | None = None,
        cpu_rt_runtime: int | None = None,
        device_cgroup_rules: list[Incomplete] | None = None,
        device_requests: list[DeviceRequest] | None = None,
        cgroupns: Literal["private", "host"] | None = None,
    ) -> None: ...

def host_config_type_error(param, param_value, expected): ...
def host_config_version_error(param, version, less_than: bool = True): ...
def host_config_value_error(param, param_value): ...
def host_config_incompatible_error(param, param_value, incompatible_param): ...

class ContainerConfig(dict[str, Incomplete]):
    def __init__(
        self,
        version: str,
        image,
        command: str | list[str],
        hostname: str | None = None,
        user: str | int | None = None,
        detach: bool = False,
        stdin_open: bool = False,
        tty: bool = False,
        ports: dict[str, int | list[int] | tuple[str, int] | None] | None = None,
        environment: dict[str, str] | list[str] | None = None,
        volumes: str | list[str] | None = None,
        network_disabled: bool = False,
        entrypoint: str | list[str] | None = None,
        working_dir: str | None = None,
        domainname: str | None = None,
        host_config: Incomplete | None = None,
        mac_address: str | None = None,
        labels: dict[str, str] | list[str] | None = None,
        stop_signal: str | None = None,
        networking_config: Incomplete | None = None,
        healthcheck: Incomplete | None = None,
        stop_timeout: int | None = None,
        runtime: str | None = None,
    ) -> None: ...
