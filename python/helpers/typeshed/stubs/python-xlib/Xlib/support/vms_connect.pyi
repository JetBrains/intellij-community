from _socket import _Address
from re import Pattern
from socket import socket
from typing_extensions import Final

from Xlib._typing import Unused

display_re: Final[Pattern[str]]

def get_display(display: str | None) -> tuple[str, None, str, int, int]: ...
def get_socket(dname: _Address, protocol: Unused, host: _Address, dno: int) -> socket: ...
def get_auth(sock: Unused, dname: Unused, host: Unused, dno: Unused) -> tuple[str, str]: ...
