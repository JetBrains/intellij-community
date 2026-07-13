from __future__ import annotations

import asyncio
from socket import AddressFamily
from typing_extensions import assert_type


async def check_getaddrinfo(loop: asyncio.AbstractEventLoop, base_loop: asyncio.BaseEventLoop) -> None:
    # The address family (item 0) is a tag that discriminates the sockaddr (item 4).
    for info in await loop.getaddrinfo("localhost", 80):
        if info[0] == AddressFamily.AF_INET:
            assert_type(info[4], "tuple[str, int]")
        elif info[0] == AddressFamily.AF_INET6:
            assert_type(info[4], "tuple[str, int, int, int] | tuple[int, bytes]")

    for info in await base_loop.getaddrinfo("localhost", 80):
        if info[0] == AddressFamily.AF_INET:
            assert_type(info[4], "tuple[str, int]")
        elif info[0] == AddressFamily.AF_INET6:
            assert_type(info[4], "tuple[str, int, int, int] | tuple[int, bytes]")
