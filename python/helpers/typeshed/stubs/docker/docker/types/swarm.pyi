from _typeshed import Incomplete
from typing import Any

class SwarmSpec(dict[str, Any]):
    def __init__(
        self,
        version,
        task_history_retention_limit: Incomplete | None = None,
        snapshot_interval: Incomplete | None = None,
        keep_old_snapshots: Incomplete | None = None,
        log_entries_for_slow_followers: Incomplete | None = None,
        heartbeat_tick: Incomplete | None = None,
        election_tick: Incomplete | None = None,
        dispatcher_heartbeat_period: Incomplete | None = None,
        node_cert_expiry: Incomplete | None = None,
        external_cas: Incomplete | None = None,
        name: Incomplete | None = None,
        labels: Incomplete | None = None,
        signing_ca_cert: Incomplete | None = None,
        signing_ca_key: Incomplete | None = None,
        ca_force_rotate: Incomplete | None = None,
        autolock_managers: Incomplete | None = None,
        log_driver: Incomplete | None = None,
    ) -> None: ...

class SwarmExternalCA(dict[str, Any]):
    def __init__(
        self, url, protocol: Incomplete | None = None, options: Incomplete | None = None, ca_cert: Incomplete | None = None
    ) -> None: ...
