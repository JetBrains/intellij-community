from collections.abc import Iterable
from typing import Any

from docker.types.services import DriverConfig
from docker.types.swarm import SwarmExternalCA

from .resource import Model

class Swarm(Model):
    id_attribute: str
    @property
    def version(self) -> str | None: ...
    def get_unlock_key(self) -> dict[str, Any]: ...
    def init(
        self,
        advertise_addr: str | None = None,
        listen_addr: str = "0.0.0.0:2377",
        force_new_cluster: bool = False,
        default_addr_pool: Iterable[str] | None = None,
        subnet_size: int | None = None,
        data_path_addr: str | None = None,
        data_path_port: int | None = None,
        *,  # Please keep in sync with docker.api.swarm.SwarmApiMixin.create_swarm_spec
        task_history_retention_limit: int | None = None,
        snapshot_interval: int | None = None,
        keep_old_snapshots: int | None = None,
        log_entries_for_slow_followers: int | None = None,
        heartbeat_tick: int | None = None,
        election_tick: int | None = None,
        dispatcher_heartbeat_period: int | None = None,
        node_cert_expiry: int | None = None,
        external_ca: SwarmExternalCA | None = None,
        external_cas: list[SwarmExternalCA] | None = None,
        name: str | None = None,
        labels: dict[str, str] | None = None,
        signing_ca_cert: str | None = None,
        signing_ca_key: str | None = None,
        ca_force_rotate: int | None = None,
        autolock_managers: bool | None = None,
        log_driver: DriverConfig | None = None,
    ) -> str: ...
    # Please keep in sync with docker.api.swarm.SwarmApiMixin.join_swarm
    def join(
        self,
        remote_addrs: list[str],
        join_token: str,
        listen_addr: str = "0.0.0.0:2377",
        advertise_addr: str | None = None,
        data_path_addr: str | None = None,
    ) -> bool: ...
    # Please keep in sync with docker.api.swarm.SwarmApiMixin.leave_swarm
    def leave(self, force: bool = False) -> bool: ...
    def reload(self) -> None: ...
    def unlock(self, key: str) -> bool: ...
    def update(
        self,
        rotate_worker_token: bool = False,
        rotate_manager_token: bool = False,
        rotate_manager_unlock_key: bool = False,
        *,  # Please keep in sync with docker.api.swarm.SwarmApiMixin.create_swarm_spec
        task_history_retention_limit: int | None = None,
        snapshot_interval: int | None = None,
        keep_old_snapshots: int | None = None,
        log_entries_for_slow_followers: int | None = None,
        heartbeat_tick: int | None = None,
        election_tick: int | None = None,
        dispatcher_heartbeat_period: int | None = None,
        node_cert_expiry: int | None = None,
        external_ca: SwarmExternalCA | None = None,
        external_cas: list[SwarmExternalCA] | None = None,
        name: str | None = None,
        labels: dict[str, str] | None = None,
        signing_ca_cert: str | None = None,
        signing_ca_key: str | None = None,
        ca_force_rotate: int | None = None,
        autolock_managers: bool | None = None,
        log_driver: DriverConfig | None = None,
    ) -> bool: ...
