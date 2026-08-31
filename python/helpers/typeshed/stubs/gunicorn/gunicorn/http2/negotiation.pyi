import socket
from typing import Final, Literal, Protocol, type_check_only

from gunicorn.config import Config

from .._types import _AddressType

@type_check_only
class _HasHeaders(Protocol):
    headers: list[tuple[str, str]]

H2C_PREFACE: Final = b"PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n"
H2C_PREFACE_TIMEOUT: Final = 1.0
MATCH: Final = "match"
PARTIAL: Final = "partial"
MISMATCH: Final = "mismatch"

def preface_match(buf: bytes) -> Literal["match", "mismatch", "partial"]: ...
def peer_trusted_for_h2c(cfg: Config, peer_addr: _AddressType) -> bool: ...
def prior_knowledge_allowed(cfg: Config, peer_addr: _AddressType) -> bool: ...
def mismatch_is_error(cfg: Config) -> bool: ...
def upgrade_allowed(cfg: Config, peer_addr: _AddressType) -> bool: ...
def read_preface_blocking(sock: socket.socket, timeout: float | None = None) -> tuple[bool, bytes]: ...

UPGRADE_101: Final[bytes]

def upgrade_settings(req: _HasHeaders) -> bytes | None: ...
