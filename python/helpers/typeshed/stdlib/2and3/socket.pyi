# Stubs for socket
# Ron Murawski <ron@horizonchess.com>

# based on: http://docs.python.org/3.2/library/socket.html
# see: http://hg.python.org/cpython/file/3d0686d90f55/Lib/socket.py
# see: http://nullege.com/codes/search/socket
# adapted for Python 2.7 by Michal Pokorny
import sys
from typing import Any, Iterable, Tuple, List, Optional, Union, overload, TypeVar


# ----- variables and constants -----

AF_UNIX: AddressFamily
AF_INET: AddressFamily
AF_INET6: AddressFamily
SOCK_STREAM: SocketKind
SOCK_DGRAM: SocketKind
SOCK_RAW: SocketKind
SOCK_RDM: SocketKind
SOCK_SEQPACKET: SocketKind
SOCK_CLOEXEC: SocketKind
SOCK_NONBLOCK: SocketKind
SOMAXCONN: int
has_ipv6: bool
_GLOBAL_DEFAULT_TIMEOUT: Any
SocketType: Any
SocketIO: Any

# These are flags that may exist on Python 3.6. Many don't exist on all platforms.
AF_AAL5: AddressFamily
AF_APPLETALK: AddressFamily
AF_ASH: AddressFamily
AF_ATMPVC: AddressFamily
AF_ATMSVC: AddressFamily
AF_AX25: AddressFamily
AF_BLUETOOTH: AddressFamily
AF_BRIDGE: AddressFamily
AF_CAN: AddressFamily
AF_DECnet: AddressFamily
AF_ECONET: AddressFamily
AF_IPX: AddressFamily
AF_IRDA: AddressFamily
AF_KEY: AddressFamily
AF_LLC: AddressFamily
AF_NETBEUI: AddressFamily
AF_NETLINK: AddressFamily
AF_NETROM: AddressFamily
AF_PACKET: AddressFamily
AF_PPPOX: AddressFamily
AF_RDS: AddressFamily
AF_ROSE: AddressFamily
AF_ROUTE: AddressFamily
AF_SECURITY: AddressFamily
AF_SNA: AddressFamily
AF_SYSTEM: AddressFamily
AF_TIPC: AddressFamily
AF_UNSPEC: AddressFamily
AF_WANPIPE: AddressFamily
AF_X25: AddressFamily
AI_ADDRCONFIG: AddressInfo
AI_ALL: AddressInfo
AI_CANONNAME: AddressInfo
AI_DEFAULT: AddressInfo
AI_MASK: AddressInfo
AI_NUMERICHOST: AddressInfo
AI_NUMERICSERV: AddressInfo
AI_PASSIVE: AddressInfo
AI_V4MAPPED: AddressInfo
AI_V4MAPPED_CFG: AddressInfo
BDADDR_ANY: str
BDADDR_LOCAL: str
BTPROTO_HCI: int
BTPROTO_L2CAP: int
BTPROTO_RFCOMM: int
BTPROTO_SCO: int
CAN_EFF_FLAG: int
CAN_EFF_MASK: int
CAN_ERR_FLAG: int
CAN_ERR_MASK: int
CAN_RAW: int
CAN_RAW_ERR_FILTER: int
CAN_RAW_FILTER: int
CAN_RAW_LOOPBACK: int
CAN_RAW_RECV_OWN_MSGS: int
CAN_RTR_FLAG: int
CAN_SFF_MASK: int
CAPI: int
EAGAIN: int
EAI_ADDRFAMILY: int
EAI_AGAIN: int
EAI_BADFLAGS: int
EAI_BADHINTS: int
EAI_FAIL: int
EAI_FAMILY: int
EAI_MAX: int
EAI_MEMORY: int
EAI_NODATA: int
EAI_NONAME: int
EAI_OVERFLOW: int
EAI_PROTOCOL: int
EAI_SERVICE: int
EAI_SOCKTYPE: int
EAI_SYSTEM: int
EBADF: int
EINTR: int
EWOULDBLOCK: int
HCI_DATA_DIR: int
HCI_FILTER: int
HCI_TIME_STAMP: int
INADDR_ALLHOSTS_GROUP: int
INADDR_ANY: int
INADDR_BROADCAST: int
INADDR_LOOPBACK: int
INADDR_MAX_LOCAL_GROUP: int
INADDR_NONE: int
INADDR_UNSPEC_GROUP: int
IPPORT_RESERVED: int
IPPORT_USERRESERVED: int
IPPROTO_AH: int
IPPROTO_BIP: int
IPPROTO_DSTOPTS: int
IPPROTO_EGP: int
IPPROTO_EON: int
IPPROTO_ESP: int
IPPROTO_FRAGMENT: int
IPPROTO_GGP: int
IPPROTO_GRE: int
IPPROTO_HELLO: int
IPPROTO_HOPOPTS: int
IPPROTO_ICMP: int
IPPROTO_ICMPV6: int
IPPROTO_IDP: int
IPPROTO_IGMP: int
IPPROTO_IP: int
IPPROTO_IPCOMP: int
IPPROTO_IPIP: int
IPPROTO_IPV4: int
IPPROTO_IPV6: int
IPPROTO_MAX: int
IPPROTO_MOBILE: int
IPPROTO_ND: int
IPPROTO_NONE: int
IPPROTO_PIM: int
IPPROTO_PUP: int
IPPROTO_RAW: int
IPPROTO_ROUTING: int
IPPROTO_RSVP: int
IPPROTO_SCTP: int
IPPROTO_TCP: int
IPPROTO_TP: int
IPPROTO_UDP: int
IPPROTO_VRRP: int
IPPROTO_XTP: int
IPV6_CHECKSUM: int
IPV6_DONTFRAG: int
IPV6_DSTOPTS: int
IPV6_HOPLIMIT: int
IPV6_HOPOPTS: int
IPV6_JOIN_GROUP: int
IPV6_LEAVE_GROUP: int
IPV6_MULTICAST_HOPS: int
IPV6_MULTICAST_IF: int
IPV6_MULTICAST_LOOP: int
IPV6_NEXTHOP: int
IPV6_PATHMTU: int
IPV6_PKTINFO: int
IPV6_RECVDSTOPTS: int
IPV6_RECVHOPLIMIT: int
IPV6_RECVHOPOPTS: int
IPV6_RECVPATHMTU: int
IPV6_RECVPKTINFO: int
IPV6_RECVRTHDR: int
IPV6_RECVTCLASS: int
IPV6_RTHDR: int
IPV6_RTHDR_TYPE_0: int
IPV6_RTHDRDSTOPTS: int
IPV6_TCLASS: int
IPV6_UNICAST_HOPS: int
IPV6_USE_MIN_MTU: int
IPV6_V6ONLY: int
IP_ADD_MEMBERSHIP: int
IP_DEFAULT_MULTICAST_LOOP: int
IP_DEFAULT_MULTICAST_TTL: int
IP_DROP_MEMBERSHIP: int
IP_HDRINCL: int
IP_MAX_MEMBERSHIPS: int
IP_MULTICAST_IF: int
IP_MULTICAST_LOOP: int
IP_MULTICAST_TTL: int
IP_OPTIONS: int
IP_RECVDSTADDR: int
IP_RECVOPTS: int
IP_RECVRETOPTS: int
IP_RETOPTS: int
IP_TOS: int
IP_TRANSPARENT: int
IP_TTL: int
IPX_TYPE: int
LOCAL_PEERCRED: int
MSG_BCAST: MsgFlag
MSG_BTAG: MsgFlag
MSG_CMSG_CLOEXEC: MsgFlag
MSG_CONFIRM: MsgFlag
MSG_CTRUNC: MsgFlag
MSG_DONTROUTE: MsgFlag
MSG_DONTWAIT: MsgFlag
MSG_EOF: MsgFlag
MSG_EOR: MsgFlag
MSG_ERRQUEUE: MsgFlag
MSG_ETAG: MsgFlag
MSG_FASTOPEN: MsgFlag
MSG_MCAST: MsgFlag
MSG_MORE: MsgFlag
MSG_NOSIGNAL: MsgFlag
MSG_NOTIFICATION: MsgFlag
MSG_OOB: MsgFlag
MSG_PEEK: MsgFlag
MSG_TRUNC: MsgFlag
MSG_WAITALL: MsgFlag
NETLINK_ARPD: int
NETLINK_CRYPTO: int
NETLINK_DNRTMSG: int
NETLINK_FIREWALL: int
NETLINK_IP6_FW: int
NETLINK_NFLOG: int
NETLINK_ROUTE6: int
NETLINK_ROUTE: int
NETLINK_SKIP: int
NETLINK_TAPBASE: int
NETLINK_TCPDIAG: int
NETLINK_USERSOCK: int
NETLINK_W1: int
NETLINK_XFRM: int
NI_DGRAM: int
NI_MAXHOST: int
NI_MAXSERV: int
NI_NAMEREQD: int
NI_NOFQDN: int
NI_NUMERICHOST: int
NI_NUMERICSERV: int
PACKET_BROADCAST: int
PACKET_FASTROUTE: int
PACKET_HOST: int
PACKET_LOOPBACK: int
PACKET_MULTICAST: int
PACKET_OTHERHOST: int
PACKET_OUTGOING: int
PF_CAN: int
PF_PACKET: int
PF_RDS: int
PF_SYSTEM: int
SCM_CREDENTIALS: int
SCM_CREDS: int
SCM_RIGHTS: int
SHUT_RD: int
SHUT_RDWR: int
SHUT_WR: int
SOL_ATALK: int
SOL_AX25: int
SOL_CAN_BASE: int
SOL_CAN_RAW: int
SOL_HCI: int
SOL_IP: int
SOL_IPX: int
SOL_NETROM: int
SOL_RDS: int
SOL_ROSE: int
SOL_SOCKET: int
SOL_TCP: int
SOL_TIPC: int
SOL_UDP: int
SO_ACCEPTCONN: int
SO_BINDTODEVICE: int
SO_BROADCAST: int
SO_DEBUG: int
SO_DONTROUTE: int
SO_ERROR: int
SO_EXCLUSIVEADDRUSE: int
SO_KEEPALIVE: int
SO_LINGER: int
SO_MARK: int
SO_OOBINLINE: int
SO_PASSCRED: int
SO_PEERCRED: int
SO_PRIORITY: int
SO_RCVBUF: int
SO_RCVLOWAT: int
SO_RCVTIMEO: int
SO_REUSEADDR: int
SO_REUSEPORT: int
SO_SETFIB: int
SO_SNDBUF: int
SO_SNDLOWAT: int
SO_SNDTIMEO: int
SO_TYPE: int
SO_USELOOPBACK: int
SYSPROTO_CONTROL: int
TCP_CORK: int
TCP_DEFER_ACCEPT: int
TCP_FASTOPEN: int
TCP_INFO: int
TCP_KEEPCNT: int
TCP_KEEPIDLE: int
TCP_KEEPINTVL: int
TCP_LINGER2: int
TCP_MAXSEG: int
TCP_NODELAY: int
TCP_NOTSENT_LOWAT: int
TCP_QUICKACK: int
TCP_SYNCNT: int
TCP_WINDOW_CLAMP: int
TIPC_ADDR_ID: int
TIPC_ADDR_NAME: int
TIPC_ADDR_NAMESEQ: int
TIPC_CFG_SRV: int
TIPC_CLUSTER_SCOPE: int
TIPC_CONN_TIMEOUT: int
TIPC_CRITICAL_IMPORTANCE: int
TIPC_DEST_DROPPABLE: int
TIPC_HIGH_IMPORTANCE: int
TIPC_IMPORTANCE: int
TIPC_LOW_IMPORTANCE: int
TIPC_MEDIUM_IMPORTANCE: int
TIPC_NODE_SCOPE: int
TIPC_PUBLISHED: int
TIPC_SRC_DROPPABLE: int
TIPC_SUB_CANCEL: int
TIPC_SUB_PORTS: int
TIPC_SUB_SERVICE: int
TIPC_SUBSCR_TIMEOUT: int
TIPC_TOP_SRV: int
TIPC_WAIT_FOREVER: int
TIPC_WITHDRAWN: int
TIPC_ZONE_SCOPE: int

if sys.version_info >= (3, 3):
    RDS_CANCEL_SENT_TO: int
    RDS_CMSG_RDMA_ARGS: int
    RDS_CMSG_RDMA_DEST: int
    RDS_CMSG_RDMA_MAP: int
    RDS_CMSG_RDMA_STATUS: int
    RDS_CMSG_RDMA_UPDATE: int
    RDS_CONG_MONITOR: int
    RDS_FREE_MR: int
    RDS_GET_MR: int
    RDS_GET_MR_FOR_DEST: int
    RDS_RDMA_DONTWAIT: int
    RDS_RDMA_FENCE: int
    RDS_RDMA_INVALIDATE: int
    RDS_RDMA_NOTIFY_ME: int
    RDS_RDMA_READWRITE: int
    RDS_RDMA_SILENT: int
    RDS_RDMA_USE_ONCE: int
    RDS_RECVERR: int

if sys.version_info >= (3, 4):
    CAN_BCM: int
    CAN_BCM_TX_SETUP: int
    CAN_BCM_TX_DELETE: int
    CAN_BCM_TX_READ: int
    CAN_BCM_TX_SEND: int
    CAN_BCM_RX_SETUP: int
    CAN_BCM_RX_DELETE: int
    CAN_BCM_RX_READ: int
    CAN_BCM_TX_STATUS: int
    CAN_BCM_TX_EXPIRED: int
    CAN_BCM_RX_STATUS: int
    CAN_BCM_RX_TIMEOUT: int
    CAN_BCM_RX_CHANGED: int
    AF_LINK: AddressFamily

if sys.version_info >= (3, 5):
    CAN_RAW_FD_FRAMES: int

if sys.version_info >= (3, 6):
    SO_DOMAIN: int
    SO_PROTOCOL: int
    SO_PEERSEC: int
    SO_PASSSEC: int
    TCP_USER_TIMEOUT: int
    TCP_CONGESTION: int
    AF_ALG: AddressFamily
    SOL_ALG: int
    ALG_SET_KEY: int
    ALG_SET_IV: int
    ALG_SET_OP: int
    ALG_SET_AEAD_ASSOCLEN: int
    ALG_SET_AEAD_AUTHSIZE: int
    ALG_SET_PUBKEY: int
    ALG_OP_DECRYPT: int
    ALG_OP_ENCRYPT: int
    ALG_OP_SIGN: int
    ALG_OP_VERIFY: int

if sys.platform == 'win32':
    SIO_RCVALL: int
    SIO_KEEPALIVE_VALS: int
    RCVALL_IPLEVEL: int
    RCVALL_MAX: int
    RCVALL_OFF: int
    RCVALL_ON: int
    RCVALL_SOCKETLEVELONLY: int

    if sys.version_info >= (3, 6):
        SIO_LOOPBACK_FAST_PATH: int

# enum versions of above flags py 3.4+
if sys.version_info >= (3, 4):
    from enum import IntEnum

    class AddressFamily(IntEnum):
        AF_UNIX = ...
        AF_INET = ...
        AF_INET6 = ...
        AF_APPLETALK = ...
        AF_ASH = ...
        AF_ATMPVC = ...
        AF_ATMSVC = ...
        AF_AX25 = ...
        AF_BLUETOOTH = ...
        AF_BRIDGE = ...
        AF_DECnet = ...
        AF_ECONET = ...
        AF_IPX = ...
        AF_IRDA = ...
        AF_KEY = ...
        AF_LLC = ...
        AF_NETBEUI = ...
        AF_NETLINK = ...
        AF_NETROM = ...
        AF_PACKET = ...
        AF_PPPOX = ...
        AF_ROSE = ...
        AF_ROUTE = ...
        AF_SECURITY = ...
        AF_SNA = ...
        AF_TIPC = ...
        AF_UNSPEC = ...
        AF_WANPIPE = ...
        AF_X25 = ...
        AF_LINK = ...

    class SocketKind(IntEnum):
        SOCK_STREAM = ...
        SOCK_DGRAM = ...
        SOCK_RAW = ...
        SOCK_RDM = ...
        SOCK_SEQPACKET = ...
        SOCK_CLOEXEC = ...
        SOCK_NONBLOCK = ...
else:
    AddressFamily = int
    SocketKind = int

if sys.version_info >= (3, 6):
    from enum import IntFlag

    class AddressInfo(IntFlag):
        AI_ADDRCONFIG = ...
        AI_ALL = ...
        AI_CANONNAME = ...
        AI_NUMERICHOST = ...
        AI_NUMERICSERV = ...
        AI_PASSIVE = ...
        AI_V4MAPPED = ...

    class MsgFlag(IntFlag):
        MSG_CTRUNC = ...
        MSG_DONTROUTE = ...
        MSG_DONTWAIT = ...
        MSG_EOR = ...
        MSG_OOB = ...
        MSG_PEEK = ...
        MSG_TRUNC = ...
        MSG_WAITALL = ...
else:
    AddressInfo = int
    MsgFlag = int


# ----- exceptions -----
class error(IOError):
    ...

class herror(error):
    def __init__(self, herror: int, string: str) -> None: ...

class gaierror(error):
    def __init__(self, error: int, string: str) -> None: ...

class timeout(error):
    ...


# Addresses can be either tuples of varying lengths (AF_INET, AF_INET6,
# AF_NETLINK, AF_TIPC) or strings (AF_UNIX).

# TODO AF_PACKET and AF_BLUETOOTH address objects

_CMSG = Tuple[int, int, bytes]
_SelfT = TypeVar('_SelfT', bound=socket)

# ----- classes -----
class socket:
    family: int
    type: int
    proto: int

    if sys.version_info < (3,):
        def __init__(self, family: int = ..., type: int = ...,
                 proto: int = ...) -> None: ...
    else:
        def __init__(self, family: int = ..., type: int = ...,
                     proto: int = ..., fileno: Optional[int] = ...) -> None: ...

    if sys.version_info >= (3, 2):
        def __enter__(self: _SelfT) -> _SelfT: ...
        def __exit__(self, *args: Any) -> None: ...

    # --- methods ---
    # second tuple item is an address
    def accept(self) -> Tuple['socket', Any]: ...
    def bind(self, address: Union[tuple, str]) -> None: ...
    def close(self) -> None: ...
    def connect(self, address: Union[tuple, str]) -> None: ...
    def connect_ex(self, address: Union[tuple, str]) -> int: ...
    def detach(self) -> int: ...
    def fileno(self) -> int: ...

    # return value is an address
    def getpeername(self) -> Any: ...
    def getsockname(self) -> Any: ...

    @overload
    def getsockopt(self, level: int, optname: int) -> int: ...
    @overload
    def getsockopt(self, level: int, optname: int, buflen: int) -> bytes: ...

    def gettimeout(self) -> float: ...
    def ioctl(self, control: object,
              option: Tuple[int, int, int]) -> None: ...
    def listen(self, backlog: int) -> None: ...
    # TODO the return value may be BinaryIO or TextIO, depending on mode
    def makefile(self, mode: str = ..., buffering: int = ...,
                 encoding: str = ..., errors: str = ...,
                 newline: str = ...) -> Any:
        ...
    def recv(self, bufsize: int, flags: int = ...) -> bytes: ...

    # return type is an address
    def recvfrom(self, bufsize: int, flags: int = ...) -> Any: ...
    def recvfrom_into(self, buffer: bytearray, nbytes: int,
                      flags: int = ...) -> Any: ...
    def recv_into(self, buffer: bytearray, nbytes: int,
                  flags: int = ...) -> Any: ...
    def send(self, data: bytes, flags: int = ...) -> int: ...
    def sendall(self, data: bytes, flags: int =...) -> None:
        ...  # return type: None on success
    @overload
    def sendto(self, data: bytes, address: Union[tuple, str]) -> int: ...
    @overload
    def sendto(self, data: bytes, flags: int, address: Union[tuple, str]) -> int: ...
    def setblocking(self, flag: bool) -> None: ...
    def settimeout(self, value: Optional[float]) -> None: ...
    def setsockopt(self, level: int, optname: int, value: Union[int, bytes]) -> None: ...
    def shutdown(self, how: int) -> None: ...

    if sys.version_info >= (3, 3):
        def recvmsg(self, __bufsize: int, __ancbufsize: int = ...,
                    __flags: int = ...) -> Tuple[bytes, List[_CMSG], int, Any]: ...
        def recvmsg_into(self, __buffers: Iterable[bytearray], __ancbufsize: int = ...,
                         __flags: int = ...) -> Tuple[int, List[_CMSG], int, Any]: ...
        def sendmsg(self, __buffers: Iterable[bytes], __ancdata: Iterable[_CMSG] = ...,
                    __flags: int = ..., __address: Any = ...) -> int: ...


# ----- functions -----
def create_connection(address: Tuple[str, int],
                      timeout: float = ...,
                      source_address: Tuple[str, int] = ...) -> socket: ...

# the 5th tuple item is an address
# TODO the "Tuple[Any, ...]" should be "Union[Tuple[str, int], Tuple[str, int, int, int]]" but that triggers
# https://github.com/python/mypy/issues/2509
def getaddrinfo(
        host: Optional[str], port: Union[str, int, None], family: int = ...,
        socktype: int = ..., proto: int = ...,
        flags: int = ...) -> List[Tuple[int, int, int, str, Tuple[Any, ...]]]:
    ...

def getfqdn(name: str = ...) -> str: ...
def gethostbyname(hostname: str) -> str: ...
def gethostbyname_ex(hostname: str) -> Tuple[str, List[str], List[str]]: ...
def gethostname() -> str: ...
def gethostbyaddr(ip_address: str) -> Tuple[str, List[str], List[str]]: ...
def getnameinfo(sockaddr: tuple, flags: int) -> Tuple[str, int]: ...
def getprotobyname(protocolname: str) -> int: ...
def getservbyname(servicename: str, protocolname: str = ...) -> int: ...
def getservbyport(port: int, protocolname: str = ...) -> str: ...
def socketpair(family: int = ...,
               type: int = ...,
               proto: int = ...) -> Tuple[socket, socket]: ...
def fromfd(fd: int, family: int, type: int, proto: int = ...) -> socket: ...
def ntohl(x: int) -> int: ...  # param & ret val are 32-bit ints
def ntohs(x: int) -> int: ...  # param & ret val are 16-bit ints
def htonl(x: int) -> int: ...  # param & ret val are 32-bit ints
def htons(x: int) -> int: ...  # param & ret val are 16-bit ints
def inet_aton(ip_string: str) -> bytes: ...  # ret val 4 bytes in length
def inet_ntoa(packed_ip: bytes) -> str: ...
def inet_pton(address_family: int, ip_string: str) -> bytes: ...
def inet_ntop(address_family: int, packed_ip: bytes) -> str: ...
def getdefaulttimeout() -> Optional[float]: ...
def setdefaulttimeout(timeout: Optional[float]) -> None: ...

if sys.version_info >= (3, 3):
    def CMSG_LEN(length: int) -> int: ...
    def CMSG_SPACE(length: int) -> int: ...
    def sethostname(name: str) -> None: ...
    def if_nameindex() -> List[Tuple[int, str]]: ...
    def if_nametoindex(name: str) -> int: ...
    def if_indextoname(index: int) -> str: ...
