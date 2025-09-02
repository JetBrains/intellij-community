from ipaddress import IPv6Address
from typing import Any

MAX_IPV6_ADDRESS_LENGTH: int

def clean_ipv6_address(
    ip_str: Any, unpack_ipv4: bool = False, error_message: str = ..., max_length: int = 39
) -> str: ...
def is_valid_ipv6_address(ip_addr: str | IPv6Address) -> bool: ...
