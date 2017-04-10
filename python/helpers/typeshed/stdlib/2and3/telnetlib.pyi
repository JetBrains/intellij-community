# Stubs for telnetlib (Python 2 and 3)

import socket
import sys
from typing import Any, Callable, Match, Optional, Pattern, Sequence, Tuple, Union

DEBUGLEVEL = ...  # type: int
TELNET_PORT = ...  # type: int

IAC = ...  # type: bytes
DONT = ...  # type: bytes
DO = ...  # type: bytes
WONT = ...  # type: bytes
WILL = ...  # type: bytes
theNULL = ...  # type: bytes

SE = ...  # type: bytes
NOP = ...  # type: bytes
DM = ...  # type: bytes
BRK = ...  # type: bytes
IP = ...  # type: bytes
AO = ...  # type: bytes
AYT = ...  # type: bytes
EC = ...  # type: bytes
EL = ...  # type: bytes
GA = ...  # type: bytes
SB = ...  # type: bytes

BINARY = ...  # type: bytes
ECHO = ...  # type: bytes
RCP = ...  # type: bytes
SGA = ...  # type: bytes
NAMS = ...  # type: bytes
STATUS = ...  # type: bytes
TM = ...  # type: bytes
RCTE = ...  # type: bytes
NAOL = ...  # type: bytes
NAOP = ...  # type: bytes
NAOCRD = ...  # type: bytes
NAOHTS = ...  # type: bytes
NAOHTD = ...  # type: bytes
NAOFFD = ...  # type: bytes
NAOVTS = ...  # type: bytes
NAOVTD = ...  # type: bytes
NAOLFD = ...  # type: bytes
XASCII = ...  # type: bytes
LOGOUT = ...  # type: bytes
BM = ...  # type: bytes
DET = ...  # type: bytes
SUPDUP = ...  # type: bytes
SUPDUPOUTPUT = ...  # type: bytes
SNDLOC = ...  # type: bytes
TTYPE = ...  # type: bytes
EOR = ...  # type: bytes
TUID = ...  # type: bytes
OUTMRK = ...  # type: bytes
TTYLOC = ...  # type: bytes
VT3270REGIME = ...  # type: bytes
X3PAD = ...  # type: bytes
NAWS = ...  # type: bytes
TSPEED = ...  # type: bytes
LFLOW = ...  # type: bytes
LINEMODE = ...  # type: bytes
XDISPLOC = ...  # type: bytes
OLD_ENVIRON = ...  # type: bytes
AUTHENTICATION = ...  # type: bytes
ENCRYPT = ...  # type: bytes
NEW_ENVIRON = ...  # type: bytes

TN3270E = ...  # type: bytes
XAUTH = ...  # type: bytes
CHARSET = ...  # type: bytes
RSP = ...  # type: bytes
COM_PORT_OPTION = ...  # type: bytes
SUPPRESS_LOCAL_ECHO = ...  # type: bytes
TLS = ...  # type: bytes
KERMIT = ...  # type: bytes
SEND_URL = ...  # type: bytes
FORWARD_X = ...  # type: bytes
PRAGMA_LOGON = ...  # type: bytes
SSPI_LOGON = ...  # type: bytes
PRAGMA_HEARTBEAT = ...  # type: bytes
EXOPL = ...  # type: bytes
NOOPT = ...  # type: bytes

class Telnet:
    def __init__(self, host: Optional[str] = ..., port: int = ...,
                 timeout: int = ...) -> None: ...
    def open(self, host: str, port: int = ..., timeout: int = ...) -> None: ...
    def msg(self, msg: str, *args: Any) -> None: ...
    def set_debuglevel(self, debuglevel: int) -> None: ...
    def close(self) -> None: ...
    def get_socket(self) -> socket.socket: ...
    def fileno(self) -> int: ...
    def write(self, buffer: bytes) -> None: ...
    def read_until(self, match: bytes, timeout: Optional[int] = ...) -> bytes: ...
    def read_all(self) -> bytes: ...
    def read_some(self) -> bytes: ...
    def read_very_eager(self) -> bytes: ...
    def read_eager(self) -> bytes: ...
    def read_lazy(self) -> bytes: ...
    def read_very_lazy(self) -> bytes: ...
    def read_sb_data(self) -> bytes: ...
    def set_option_negotiation_callback(self, callback: Optional[Callable[[socket.socket, bytes, bytes], Any]]) -> None: ...
    def process_rawq(self) -> None: ...
    def rawq_getchar(self) -> bytes: ...
    def fill_rawq(self) -> None: ...
    def sock_avail(self) -> bool: ...
    def interact(self) -> None: ...
    def mt_interact(self) -> None: ...
    def listener(self) -> None: ...
    def expect(self, list: Sequence[Union[Pattern[bytes], bytes]], timeout: Optional[int] = ...) -> Tuple[int, Optional[Match[bytes]], bytes]: ...
    if sys.version_info >= (3, 6):
        def __enter__(self) -> Telnet: ...
        def __exit__(self, type: Any, value: Any, traceback: Any) -> None: ...
