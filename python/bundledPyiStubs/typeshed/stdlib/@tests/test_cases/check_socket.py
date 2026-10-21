from __future__ import annotations

import socket
from typing_extensions import assert_type


def check_getaddrinfo() -> None:
    # The address family (item 0) is a tag that discriminates the sockaddr (item 4).
    for info in socket.getaddrinfo("localhost", 80):
        if info[0] == socket.AddressFamily.AF_INET:
            assert_type(info[4], "tuple[str, int]")
        elif info[0] == socket.AddressFamily.AF_INET6:
            assert_type(info[4], "tuple[str, int, int, int] | tuple[int, bytes]")
