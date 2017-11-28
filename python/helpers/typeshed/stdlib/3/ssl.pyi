# Stubs for ssl (Python 3.4)

from typing import (
    Any, Dict, Callable, List, NamedTuple, Optional, Set, Tuple, Union,
)
import socket
import sys

_PCTRTT = Tuple[Tuple[str, str], ...]
_PCTRTTT = Tuple[_PCTRTT, ...]
_PeerCertRetDictType = Dict[str, Union[str, _PCTRTTT, _PCTRTT]]
_PeerCertRetType = Union[_PeerCertRetDictType, bytes, None]
_EnumRetType = List[Tuple[bytes, str, Union[Set[str], bool]]]
_PasswordType = Union[Callable[[], Union[str, bytes]], str, bytes]


if sys.version_info >= (3, 5):
    _SC1ArgT = Union[SSLSocket, SSLObject]
else:
    _SC1ArgT = SSLSocket
_SrvnmeCbType = Callable[[_SC1ArgT, Optional[str], 'SSLSocket'], Optional[int]]

class SSLError(OSError):
    library = ...  # type: str
    reason = ...  # type: str
class SSLZeroReturnError(SSLError): ...
class SSLWantReadError(SSLError): ...
class SSLWantWriteError(SSLError): ...
class SSLSyscallError(SSLError): ...
class SSLEOFError(SSLError): ...
class CertificateError(Exception): ...


def wrap_socket(sock: socket.socket, keyfile: Optional[str] = ...,
                certfile: Optional[str] = ..., server_side: bool = ...,
                cert_reqs: int = ..., ssl_version: int = ...,
                ca_certs: Optional[str] = ...,
                do_handshake_on_connect: bool = ...,
                suppress_ragged_eofs: bool = ...,
                ciphers: Optional[str] = ...) -> 'SSLSocket': ...


if sys.version_info >= (3, 4):
    def create_default_context(purpose: Any = ..., *,
                               cafile: Optional[str] = ...,
                               capath: Optional[str] = ...,
                               cadata: Optional[str] = ...) -> 'SSLContext': ...

    def _create_unverified_context(protocol: int = ..., *,
                                   cert_reqs: int = ...,
                                   check_hostname: bool = ...,
                                   purpose: Any = ...,
                                   certfile: Optional[str] = ...,
                                   keyfile: Optional[str] = ...,
                                   cafile: Optional[str] = ...,
                                   capath: Optional[str] = ...,
                                   cadata: Optional[str] = ...) -> 'SSLContext': ...
    _create_default_https_context = ...  # type: Callable[..., 'SSLContext']

def RAND_bytes(num: int) -> bytes: ...
def RAND_pseudo_bytes(num: int) -> Tuple[bytes, bool]: ...
def RAND_status() -> bool: ...
def RAND_egd(path: str) -> None: ...
def RAND_add(bytes: bytes, entropy: float) -> None: ...


def match_hostname(cert: _PeerCertRetType, hostname: str) -> None: ...
def cert_time_to_seconds(cert_time: str) -> int: ...
def get_server_certificate(addr: Tuple[str, int], ssl_version: int = ...,
                           ca_certs: Optional[str] = ...) -> str: ...
def DER_cert_to_PEM_cert(der_cert_bytes: bytes) -> str: ...
def PEM_cert_to_DER_cert(pem_cert_string: str) -> bytes: ...
if sys.version_info >= (3, 4):
    DefaultVerifyPaths = NamedTuple('DefaultVerifyPaths',
                                    [('cafile', str), ('capath', str),
                                     ('openssl_cafile_env', str),
                                     ('openssl_cafile', str),
                                     ('openssl_capath_env', str),
                                     ('openssl_capath', str)])
    def get_default_verify_paths() -> DefaultVerifyPaths: ...

if sys.version_info >= (3, 4) and sys.platform == 'win32':
    def enum_certificates(store_name: str) -> _EnumRetType: ...
    def enum_crls(store_name: str) -> _EnumRetType: ...


CERT_NONE = ...  # type: int
CERT_OPTIONAL = ...  # type: int
CERT_REQUIRED = ...  # type: int

if sys.version_info >= (3, 4):
    VERIFY_DEFAULT = ...  # type: int
    VERIFY_CRL_CHECK_LEAF = ...  # type: int
    VERIFY_CRL_CHECK_CHAIN = ...  # type: int
    VERIFY_X509_STRICT = ...  # type: int
    VERIFY_X509_TRUSTED_FIRST = ...  # type: int

PROTOCOL_SSLv23 = ...  # type: int
PROTOCOL_SSLv2 = ...  # type: int
PROTOCOL_SSLv3 = ...  # type: int
PROTOCOL_TLSv1 = ...  # type: int
if sys.version_info >= (3, 4):
    PROTOCOL_TLSv1_1 = ...  # type: int
    PROTOCOL_TLSv1_2 = ...  # type: int
if sys.version_info >= (3, 5):
    PROTOCOL_TLS = ...  # type: int
if sys.version_info >= (3, 6):
    PROTOCOL_TLS_CLIENT = ...  # type: int
    PROTOCOL_TLS_SERVER = ...  # type: int

OP_ALL = ...  # type: int
OP_NO_SSLv2 = ...  # type: int
OP_NO_SSLv3 = ...  # type: int
OP_NO_TLSv1 = ...  # type: int
if sys.version_info >= (3, 4):
    OP_NO_TLSv1_1 = ...  # type: int
    OP_NO_TLSv1_2 = ...  # type: int
OP_CIPHER_SERVER_PREFERENCE = ...  # type: int
OP_SINGLE_DH_USE = ...  # type: int
OP_SINGLE_ECDH_USE = ...  # type: int
OP_NO_COMPRESSION = ...  # type: int
if sys.version_info >= (3, 6):
    OP_NO_TICKET = ...  # type: int

if sys.version_info >= (3, 5):
    HAS_ALPN = ...  # type: int
HAS_ECDH = ...  # type: bool
HAS_SNI = ...  # type: bool
HAS_NPN = ...  # type: bool
CHANNEL_BINDING_TYPES = ...  # type: List[str]

OPENSSL_VERSION = ...  # type: str
OPENSSL_VERSION_INFO = ...  # type: Tuple[int, int, int, int, int]
OPENSSL_VERSION_NUMBER = ...  # type: int

if sys.version_info >= (3, 4):
    ALERT_DESCRIPTION_HANDSHAKE_FAILURE = ...  # type: int
    ALERT_DESCRIPTION_INTERNAL_ERROR = ...  # type: int
    ALERT_DESCRIPTION_ACCESS_DENIED = ...  # type: int
    ALERT_DESCRIPTION_BAD_CERTIFICATE = ...  # type: int
    ALERT_DESCRIPTION_BAD_CERTIFICATE_HASH_VALUE = ...  # type: int
    ALERT_DESCRIPTION_BAD_CERTIFICATE_STATUS_RESPONSE = ...  # type: int
    ALERT_DESCRIPTION_BAD_RECORD_MAC = ...  # type: int
    ALERT_DESCRIPTION_CERTIFICATE_EXPIRED = ...  # type: int
    ALERT_DESCRIPTION_CERTIFICATE_REVOKED = ...  # type: int
    ALERT_DESCRIPTION_CERTIFICATE_UNKNOWN = ...  # type: int
    ALERT_DESCRIPTION_CERTIFICATE_UNOBTAINABLE = ...  # type: int
    ALERT_DESCRIPTION_CLOSE_NOTIFY = ...  # type: int
    ALERT_DESCRIPTION_DECODE_ERROR = ...  # type: int
    ALERT_DESCRIPTION_DECOMPRESSION_FAILURE = ...  # type: int
    ALERT_DESCRIPTION_DECRYPT_ERROR = ...  # type: int
    ALERT_DESCRIPTION_ILLEGAL_PARAMETER = ...  # type: int
    ALERT_DESCRIPTION_INSUFFICIENT_SECURITY = ...  # type: int
    ALERT_DESCRIPTION_NO_RENEGOTIATION = ...  # type: int
    ALERT_DESCRIPTION_PROTOCOL_VERSION = ...  # type: int
    ALERT_DESCRIPTION_RECORD_OVERFLOW = ...  # type: int
    ALERT_DESCRIPTION_UNEXPECTED_MESSAGE = ...  # type: int
    ALERT_DESCRIPTION_UNKNOWN_CA = ...  # type: int
    ALERT_DESCRIPTION_UNKNOWN_PSK_IDENTITY = ...  # type: int
    ALERT_DESCRIPTION_UNRECOGNIZED_NAME = ...  # type: int
    ALERT_DESCRIPTION_UNSUPPORTED_CERTIFICATE = ...  # type: int
    ALERT_DESCRIPTION_UNSUPPORTED_EXTENSION = ...  # type: int
    ALERT_DESCRIPTION_USER_CANCELLED = ...  # type: int

if sys.version_info >= (3, 4):
    _PurposeType = NamedTuple('_PurposeType',
                             [('nid', int), ('shortname', str),
                              ('longname', str), ('oid', str)])
    class Purpose:
        SERVER_AUTH = ...  # type: _PurposeType
        CLIENT_AUTH = ...  # type: _PurposeType


class SSLSocket(socket.socket):
    context = ...  # type: SSLContext
    server_side = ...  # type: bool
    server_hostname = ...  # type: Optional[str]
    if sys.version_info >= (3, 6):
        session = ...  # type: Optional[SSLSession]
        session_reused = ...  # type: Optional[bool]

    def read(self, len: int = ...,
             buffer: Optional[bytearray] = ...) -> bytes: ...
    def write(self, buf: bytes) -> int: ...
    def do_handshake(self) -> None: ...
    def getpeercert(self, binary_form: bool = ...) -> _PeerCertRetType: ...
    def cipher(self) -> Tuple[str, int, int]: ...
    if sys.version_info >= (3, 5):
        def shared_cipher(self) -> Optional[List[Tuple[str, int, int]]]: ...
    def compression(self) -> Optional[str]: ...
    def get_channel_binding(self, cb_type: str = ...) -> Optional[bytes]: ...
    if sys.version_info >= (3, 5):
        def selected_alpn_protocol(self) -> Optional[str]: ...
    def selected_npn_protocol(self) -> Optional[str]: ...
    def unwrap(self) -> socket.socket: ...
    if sys.version_info >= (3, 5):
        def version(self) -> Optional[str]: ...
    def pending(self) -> int: ...


class SSLContext:
    if sys.version_info >= (3, 4):
        check_hostname = ...  # type: bool
    options = ...  # type: int
    @property
    def protocol(self) -> int: ...
    if sys.version_info >= (3, 4):
        verify_flags = ...  # type: int
    verify_mode = ...  # type: int
    def __init__(self, protocol: int) -> None: ...
    if sys.version_info >= (3, 4):
        def cert_store_stats(self) -> Dict[str, int]: ...
    def load_cert_chain(self, certfile: str, keyfile: Optional[str] = ...,
                        password: _PasswordType = ...) -> None: ...
    if sys.version_info >= (3, 4):
        def load_default_certs(self, purpose: _PurposeType = ...) -> None: ...
        def load_verify_locations(self, cafile: Optional[str] = ...,
                                  capath: Optional[str] = ...,
                                  cadata: Union[str, bytes, None] = ...) -> None: ...
        def get_ca_certs(self,
                         binary_form: bool = ...) -> Union[List[_PeerCertRetDictType], List[bytes]]: ...
    else:
        def load_verify_locations(self,
                                  cafile: Optional[str] = ...,
                                  capath: Optional[str] = ...) -> None: ...
    def set_default_verify_paths(self) -> None: ...
    def set_ciphers(self, ciphers: str) -> None: ...
    if sys.version_info >= (3, 5):
        def set_alpn_protocols(self, protocols: List[str]) -> None: ...
    def set_npn_protocols(self, protocols: List[str]) -> None: ...
    def set_servername_callback(self,
                                server_name_callback: Optional[_SrvnmeCbType]) -> None: ...
    def load_dh_params(self, dhfile: str) -> None: ...
    def set_ecdh_curve(self, curve_name: str) -> None: ...
    def wrap_socket(self, sock: socket.socket, server_side: bool = ...,
                    do_handshake_on_connect: bool = ...,
                    suppress_ragged_eofs: bool = ...,
                    server_hostname: Optional[str] = ...) -> SSLSocket: ...
    if sys.version_info >= (3, 5):
        def wrap_bio(self, incoming: 'MemoryBIO', outgoing: 'MemoryBIO',
                     server_side: bool = ...,
                     server_hostname: Optional[str] = ...) -> 'SSLObject': ...
    def session_stats(self) -> Dict[str, int]: ...


if sys.version_info >= (3, 5):
    class SSLObject:
        context = ...  # type: SSLContext
        server_side = ...  # type: bool
        server_hostname = ...  # type: Optional[str]
        if sys.version_info >= (3, 6):
            session = ...  # type: Optional[SSLSession]
            session_reused = ...  # type: bool
        def read(self, len: int = ...,
                 buffer: Optional[bytearray] = ...) -> bytes: ...
        def write(self, buf: bytes) -> int: ...
        def getpeercert(self, binary_form: bool = ...) -> _PeerCertRetType: ...
        def selected_npn_protocol(self) -> Optional[str]: ...
        def cipher(self) -> Tuple[str, int, int]: ...
        def shared_cipher(self) -> Optional[List[Tuple[str, int, int]]]: ...
        def compression(self) -> Optional[str]: ...
        def pending(self) -> int: ...
        def do_handshake(self) -> None: ...
        def unwrap(self) -> None: ...
        def get_channel_binding(self, cb_type: str = ...) -> Optional[bytes]: ...

    class MemoryBIO:
        pending = ...  # type: int
        eof = ...  # type: bool
        def read(self, n: int = ...) -> bytes: ...
        def write(self, buf: bytes) -> int: ...
        def write_eof(self) -> None: ...

if sys.version_info >= (3, 6):
    class SSLSession:
        id = ...  # type: bytes
        time = ...  # type: int
        timeout = ...  # type: int
        ticket_lifetime_hint = ...  # type: int
        has_ticket = ...  # type: bool


# TODO below documented in cpython but not in docs.python.org
# taken from python 3.4
SSL_ERROR_EOF = ...  # type: int
SSL_ERROR_INVALID_ERROR_CODE = ...  # type: int
SSL_ERROR_SSL = ...  # type: int
SSL_ERROR_SYSCALL = ...  # type: int
SSL_ERROR_WANT_CONNECT = ...  # type: int
SSL_ERROR_WANT_READ = ...  # type: int
SSL_ERROR_WANT_WRITE = ...  # type: int
SSL_ERROR_WANT_X509_LOOKUP = ...  # type: int
SSL_ERROR_ZERO_RETURN = ...  # type: int

def get_protocol_name(protocol_code: int) -> str: ...

AF_INET = ...  # type: int
PEM_FOOTER = ...  # type: str
PEM_HEADER = ...  # type: str
SOCK_STREAM = ...  # type: int
SOL_SOCKET = ...  # type: int
SO_TYPE = ...  # type: int
