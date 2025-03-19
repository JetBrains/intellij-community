from _typeshed import Incomplete

log: Incomplete

class SwarmApiMixin:
    def create_swarm_spec(self, *args, **kwargs): ...
    def get_unlock_key(self): ...
    def init_swarm(
        self,
        advertise_addr: Incomplete | None = None,
        listen_addr: str = "0.0.0.0:2377",
        force_new_cluster: bool = False,
        swarm_spec: Incomplete | None = None,
        default_addr_pool: Incomplete | None = None,
        subnet_size: Incomplete | None = None,
        data_path_addr: Incomplete | None = None,
        data_path_port: Incomplete | None = None,
    ): ...
    def inspect_swarm(self): ...
    def inspect_node(self, node_id): ...
    def join_swarm(
        self,
        remote_addrs,
        join_token,
        listen_addr: str = "0.0.0.0:2377",
        advertise_addr: Incomplete | None = None,
        data_path_addr: Incomplete | None = None,
    ): ...
    def leave_swarm(self, force: bool = False): ...
    def nodes(self, filters: Incomplete | None = None): ...
    def remove_node(self, node_id, force: bool = False): ...
    def unlock_swarm(self, key): ...
    def update_node(self, node_id, version, node_spec: Incomplete | None = None): ...
    def update_swarm(
        self,
        version,
        swarm_spec: Incomplete | None = None,
        rotate_worker_token: bool = False,
        rotate_manager_token: bool = False,
        rotate_manager_unlock_key: bool = False,
    ): ...
