import datetime
from _typeshed import Incomplete
from collections.abc import Iterable, Mapping
from typing import Any, Literal, TypeAlias, TypedDict, overload, type_check_only
from typing_extensions import NotRequired

from docker._types import ContainerWeightDevice, WaitContainerResponse
from docker.types.containers import DeviceRequest, LogConfig, Ulimit
from docker.types.daemon import CancellableStream
from docker.types.healthcheck import Healthcheck
from docker.types.services import Mount

from ..types import ContainerConfig, EndpointConfig, HostConfig, NetworkingConfig

@type_check_only
class _RestartPolicy(TypedDict):
    MaximumRetryCount: NotRequired[int]
    Name: NotRequired[Literal["always", "on-failure"]]

@type_check_only
class _HasId(TypedDict):
    Id: str

@type_check_only
class _HasID(TypedDict):
    ID: str

@type_check_only
class _TopResult(TypedDict):
    Titles: list[str]
    Processes: list[list[str]]

_Container: TypeAlias = _HasId | _HasID | str

class ContainerApiMixin:
    @overload
    def attach(
        self,
        container: _Container,
        stdout: bool = True,
        stderr: bool = True,
        stream: Literal[False] = False,
        logs: bool = False,
        demux: Literal[False] = False,
    ) -> bytes: ...
    @overload
    def attach(
        self,
        container: _Container,
        stdout: bool = True,
        stderr: bool = True,
        stream: Literal[False] = False,
        logs: bool = False,
        *,
        demux: Literal[True],
    ) -> tuple[bytes | None, bytes | None]: ...
    @overload
    def attach(
        self,
        container: _Container,
        stdout: bool = True,
        stderr: bool = True,
        *,
        stream: Literal[True],
        logs: bool = False,
        demux: Literal[False] = False,
    ) -> CancellableStream[bytes]: ...
    @overload
    def attach(
        self,
        container: _Container,
        stdout: bool = True,
        stderr: bool = True,
        *,
        stream: Literal[True],
        logs: bool = False,
        demux: Literal[True],
    ) -> CancellableStream[tuple[bytes | None, bytes | None]]: ...

    def attach_socket(self, container: _Container, params=None, ws: bool = False): ...
    def commit(
        self,
        container: _Container,
        repository: str | None = None,
        tag: str | None = None,
        message=None,
        author=None,
        pause: bool = True,
        changes=None,
        conf=None,
    ): ...
    def containers(
        self,
        quiet: bool = False,
        all: bool = False,
        trunc: bool = False,
        latest: bool = False,
        since: str | None = None,
        before: str | None = None,
        limit: int = -1,
        size: bool = False,
        filters=None,
    ): ...
    def create_container(
        self,
        image,
        command: str | list[str] | None = None,
        hostname: str | None = None,
        user: str | int | None = None,
        detach: bool = False,
        stdin_open: bool = False,
        tty: bool = False,
        # list is invariant, enumerating all possible union combination would be too complex for:
        # list[str | int | tuple[int | str, str] | tuple[int | str, ...]]
        ports: dict[str, dict[Incomplete, Incomplete]] | list[Any] | None = None,
        environment: dict[str, str] | list[str] | None = None,
        volumes: str | list[str] | None = None,
        network_disabled: bool = False,
        name: str | None = None,
        entrypoint: str | list[str] | None = None,
        working_dir: str | None = None,
        domainname: str | None = None,
        host_config=None,
        mac_address: str | None = None,
        labels: dict[str, str] | list[str] | None = None,
        stop_signal: str | None = None,
        networking_config=None,
        healthcheck=None,
        stop_timeout: int | None = None,
        runtime: str | None = None,
        use_config_proxy: bool = True,
        platform: str | None = None,
    ): ...
    # Please keep in sync with docker.types.ContainerConfig
    def create_container_config(
        self,
        image: str,
        command: str | list[str],
        hostname: str | None = None,
        user: str | int | None = None,
        detach: bool = False,
        stdin_open: bool = False,
        tty: bool = False,
        # list is invariant, enumerating all possible union combination would be too complex for:
        # list[str | int | tuple[int | str, str] | tuple[int | str, ...]]
        ports: dict[str, dict[str, str]] | list[Any] | None = None,
        environment: dict[str, str] | list[str] | None = None,
        volumes: str | list[str] | None = None,
        network_disabled: bool = False,
        entrypoint: str | list[str] | None = None,
        working_dir: str | None = None,
        domainname: str | None = None,
        host_config: HostConfig | None = None,
        mac_address: str | None = None,
        labels: dict[str, str] | list[str] | None = None,
        stop_signal: str | None = None,
        networking_config: NetworkingConfig | None = None,
        healthcheck: Healthcheck | None = None,
        stop_timeout: int | None = None,
        runtime: str | None = None,
    ) -> ContainerConfig: ...
    def create_container_from_config(self, config, name=None, platform=None): ...
    # Please keep in sync with docker.types.HostConfig
    def create_host_config(
        self,
        binds: dict[str, Mapping[str, str]] | list[str] | None = None,
        port_bindings: Mapping[int | str, Any] | None = None,  # Any: int, str, tuple, dict, or list
        lxc_conf: dict[str, str] | list[dict[str, str]] | None = None,
        publish_all_ports: bool = False,
        links: dict[str, str] | dict[str, None] | dict[str, str | None] | Iterable[tuple[str, str | None]] | None = None,
        privileged: bool = False,
        dns: list[str] | None = None,
        dns_search: list[str] | None = None,
        volumes_from: list[str] | None = None,
        network_mode: str | None = None,
        restart_policy: Mapping[str, str | int] | None = None,
        cap_add: list[str] | None = None,
        cap_drop: list[str] | None = None,
        devices: list[str] | None = None,
        extra_hosts: dict[str, str] | list[str] | None = None,
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
        group_add: Iterable[str | int] | None = None,
        cpu_quota: int | None = None,
        cpu_period: int | None = None,
        blkio_weight: int | None = None,
        blkio_weight_device: list[ContainerWeightDevice] | None = None,
        device_read_bps: list[Mapping[str, str | int]] | None = None,
        device_write_bps: list[Mapping[str, str | int]] | None = None,
        device_read_iops: list[Mapping[str, str | int]] | None = None,
        device_write_iops: list[Mapping[str, str | int]] | None = None,
        oom_kill_disable: bool = False,
        shm_size: str | int | None = None,
        sysctls: dict[str, str] | None = None,
        tmpfs: dict[str, str] | None = None,
        oom_score_adj: int | None = None,
        dns_opt: list[str] | None = None,
        cpu_shares: int | None = None,
        cpuset_cpus: str | None = None,
        userns_mode: str | None = None,
        uts_mode: str | None = None,
        pids_limit: int | None = None,
        isolation: str | None = None,
        auto_remove: bool = False,
        storage_opt: dict[str, str] | None = None,
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
        device_cgroup_rules: list[str] | None = None,
        device_requests: list[DeviceRequest] | None = None,
        cgroupns: Literal["private", "host"] | None = None,
    ) -> HostConfig: ...
    # Please keep in sync with docker.types.NetworkingConfig
    def create_networking_config(self, endpoints_config: EndpointConfig | None = None) -> NetworkingConfig: ...
    # Please keep in sync with docker.types.EndpointConfig
    def create_endpoint_config(
        self,
        aliases: list[str] | None = None,
        links: dict[str, str] | dict[str, None] | dict[str, str | None] | Iterable[tuple[str, str | None]] | None = None,
        ipv4_address: str | None = None,
        ipv6_address: str | None = None,
        link_local_ips: list[str] | None = None,
        driver_opt: dict[str, str] | None = None,
        mac_address: str | None = None,
    ) -> EndpointConfig: ...
    def diff(self, container: _Container) -> list[dict[Incomplete, Incomplete]]: ...
    def export(self, container: _Container, chunk_size: int | None = 2097152): ...
    def get_archive(
        self, container: _Container, path, chunk_size: int | None = 2097152, encode_stream: bool = False
    ) -> tuple[Incomplete, Incomplete]: ...
    def inspect_container(self, container: _Container): ...
    def kill(self, container: _Container, signal: str | int | None = None) -> None: ...

    @overload
    def logs(
        self,
        container: _Container,
        stdout: bool = True,
        stderr: bool = True,
        *,
        stream: Literal[True],
        timestamps: bool = False,
        tail: Literal["all"] | int = "all",
        since: datetime.datetime | float | None = None,
        follow: bool | None = None,
        until: datetime.datetime | float | None = None,
    ) -> CancellableStream[bytes]: ...
    @overload
    def logs(
        self,
        container: _Container,
        stdout: bool,
        stderr: bool,
        stream: Literal[True],
        timestamps: bool = False,
        tail: Literal["all"] | int = "all",
        since: datetime.datetime | float | None = None,
        follow: bool | None = None,
        until: datetime.datetime | float | None = None,
    ) -> CancellableStream[bytes]: ...
    @overload
    def logs(
        self,
        container: _Container,
        stdout: bool = True,
        stderr: bool = True,
        stream: Literal[False] = False,
        timestamps: bool = False,
        tail: Literal["all"] | int = "all",
        since: datetime.datetime | float | None = None,
        follow: bool | None = None,
        until: datetime.datetime | float | None = None,
    ) -> bytes: ...

    def pause(self, container: _Container) -> None: ...
    def port(self, container: _Container, private_port: int): ...
    def put_archive(self, container: _Container, path: str, data) -> bool: ...
    def prune_containers(self, filters=None): ...
    def remove_container(self, container: _Container, v: bool = False, link: bool = False, force: bool = False) -> None: ...
    def rename(self, container: _Container, name: str) -> None: ...
    def resize(self, container: _Container, height: int, width: int) -> None: ...
    def restart(self, container: _Container, timeout: int = 10) -> None: ...
    def start(self, container: _Container) -> None: ...
    def stats(self, container: _Container, decode: bool | None = None, stream: bool = True, one_shot: bool | None = None): ...
    def stop(self, container: _Container, timeout: int | None = None) -> None: ...
    def top(self, container: _Container, ps_args: str | None = None) -> _TopResult: ...
    def unpause(self, container: _Container) -> None: ...
    def update_container(
        self,
        container: _Container,
        blkio_weight: int | None = None,
        cpu_period: int | None = None,
        cpu_quota: int | None = None,
        cpu_shares: int | None = None,
        cpuset_cpus: str | None = None,
        cpuset_mems: str | None = None,
        mem_limit: float | str | None = None,
        mem_reservation: float | str | None = None,
        memswap_limit: int | str | None = None,
        kernel_memory: int | str | None = None,
        restart_policy: _RestartPolicy | None = None,
    ): ...
    def wait(
        self,
        container: _Container,
        timeout: int | None = None,
        condition: Literal["not-running", "next-exit", "removed"] | None = None,
    ) -> WaitContainerResponse: ...
