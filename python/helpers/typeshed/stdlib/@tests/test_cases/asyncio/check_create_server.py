import asyncio
import socket


async def check_abstract_event_loop(loop: asyncio.AbstractEventLoop, sock: socket.socket) -> None:
    await loop.create_server(asyncio.Protocol, None, 8000)
    await loop.create_server(asyncio.Protocol, "localhost")
    await loop.create_server(asyncio.Protocol, "localhost", None)
    await loop.create_server(asyncio.Protocol, "localhost", 8000)
    await loop.create_server(asyncio.Protocol, port=8000)
    await loop.create_server(asyncio.Protocol, sock=sock)

    await loop.create_server(asyncio.Protocol)  # type: ignore
    await loop.create_server(asyncio.Protocol, "localhost", sock=sock)  # type: ignore


async def check_base_event_loop(loop: asyncio.BaseEventLoop, sock: socket.socket) -> None:
    await loop.create_server(asyncio.Protocol, None, 8000)
    await loop.create_server(asyncio.Protocol, "localhost")
    await loop.create_server(asyncio.Protocol, "localhost", None)
    await loop.create_server(asyncio.Protocol, "localhost", 8000)
    await loop.create_server(asyncio.Protocol, port=8000)
    await loop.create_server(asyncio.Protocol, sock=sock)

    await loop.create_server(asyncio.Protocol)  # type: ignore
    await loop.create_server(asyncio.Protocol, "localhost", sock=sock)  # type: ignore
