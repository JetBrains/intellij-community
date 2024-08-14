from _typeshed import Incomplete

class EndpointConfig(dict[str, Incomplete]):
    def __init__(
        self,
        version,
        aliases: Incomplete | None = None,
        links: Incomplete | None = None,
        ipv4_address: Incomplete | None = None,
        ipv6_address: Incomplete | None = None,
        link_local_ips: Incomplete | None = None,
        driver_opt: Incomplete | None = None,
        mac_address: Incomplete | None = None,
    ) -> None: ...

class NetworkingConfig(dict[str, Incomplete]):
    def __init__(self, endpoints_config: Incomplete | None = None) -> None: ...

class IPAMConfig(dict[str, Incomplete]):
    def __init__(
        self, driver: str = "default", pool_configs: Incomplete | None = None, options: Incomplete | None = None
    ) -> None: ...

class IPAMPool(dict[str, Incomplete]):
    def __init__(
        self,
        subnet: Incomplete | None = None,
        iprange: Incomplete | None = None,
        gateway: Incomplete | None = None,
        aux_addresses: Incomplete | None = None,
    ) -> None: ...
