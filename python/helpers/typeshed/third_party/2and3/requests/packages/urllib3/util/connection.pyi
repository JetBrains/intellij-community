from typing import Any

poll = ...  # type: Any
select = ...  # type: Any
HAS_IPV6 = ...  # type: bool

def is_connection_dropped(conn): ...
def create_connection(address, timeout=..., source_address=..., socket_options=...): ...
