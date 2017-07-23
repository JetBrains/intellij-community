# Stubs for socket
# Ron Murawski <ron@horizonchess.com>

# based on: http://docs.python.org/3.2/library/socket.html
# see: http://hg.python.org/cpython/file/3d0686d90f55/Lib/socket.py
# see: http://nullege.com/codes/search/socket
# adapted for Python 2.7 by Michal Pokorny

from typing import Any, Tuple, List, Optional, Union, overload

# ----- variables and constants -----

AF_UNIX = 0
AF_INET = 0
AF_INET6 = 0
SOCK_STREAM = 0
SOCK_DGRAM = 0
SOCK_RAW = 0
SOCK_RDM = 0
SOCK_SEQPACKET = 0
SOCK_CLOEXEC = 0
SOCK_NONBLOCK = 0
SOMAXCONN = 0
has_ipv6 = False
_GLOBAL_DEFAULT_TIMEOUT = ...  # type: Any
SocketType = ...  # type: Any
SocketIO = ...  # type: Any


# the following constants are included with Python 3.2.3 (Ubuntu)
# some of the constants may be Linux-only
# all Windows/Mac-specific constants are absent
AF_APPLETALK = 0
AF_ASH = 0
AF_ATMPVC = 0
AF_ATMSVC = 0
AF_AX25 = 0
AF_BLUETOOTH = 0
AF_BRIDGE = 0
AF_DECnet = 0
AF_ECONET = 0
AF_IPX = 0
AF_IRDA = 0
AF_KEY = 0
AF_LLC = 0
AF_NETBEUI = 0
AF_NETLINK = 0
AF_NETROM = 0
AF_PACKET = 0
AF_PPPOX = 0
AF_ROSE = 0
AF_ROUTE = 0
AF_SECURITY = 0
AF_SNA = 0
AF_TIPC = 0
AF_UNSPEC = 0
AF_WANPIPE = 0
AF_X25 = 0
AI_ADDRCONFIG = 0
AI_ALL = 0
AI_CANONNAME = 0
AI_NUMERICHOST = 0
AI_NUMERICSERV = 0
AI_PASSIVE = 0
AI_V4MAPPED = 0
BDADDR_ANY = 0
BDADDR_LOCAL = 0
BTPROTO_HCI = 0
BTPROTO_L2CAP = 0
BTPROTO_RFCOMM = 0
BTPROTO_SCO = 0
CAPI = 0
EAGAIN = 0
EAI_ADDRFAMILY = 0
EAI_AGAIN = 0
EAI_BADFLAGS = 0
EAI_FAIL = 0
EAI_FAMILY = 0
EAI_MEMORY = 0
EAI_NODATA = 0
EAI_NONAME = 0
EAI_OVERFLOW = 0
EAI_SERVICE = 0
EAI_SOCKTYPE = 0
EAI_SYSTEM = 0
EBADF = 0
EINTR = 0
EWOULDBLOCK = 0
HCI_DATA_DIR = 0
HCI_FILTER = 0
HCI_TIME_STAMP = 0
INADDR_ALLHOSTS_GROUP = 0
INADDR_ANY = 0
INADDR_BROADCAST = 0
INADDR_LOOPBACK = 0
INADDR_MAX_LOCAL_GROUP = 0
INADDR_NONE = 0
INADDR_UNSPEC_GROUP = 0
IPPORT_RESERVED = 0
IPPORT_USERRESERVED = 0
IPPROTO_AH = 0
IPPROTO_DSTOPTS = 0
IPPROTO_EGP = 0
IPPROTO_ESP = 0
IPPROTO_FRAGMENT = 0
IPPROTO_GRE = 0
IPPROTO_HOPOPTS = 0
IPPROTO_ICMP = 0
IPPROTO_ICMPV6 = 0
IPPROTO_IDP = 0
IPPROTO_IGMP = 0
IPPROTO_IP = 0
IPPROTO_IPIP = 0
IPPROTO_IPV6 = 0
IPPROTO_NONE = 0
IPPROTO_PIM = 0
IPPROTO_PUP = 0
IPPROTO_RAW = 0
IPPROTO_ROUTING = 0
IPPROTO_RSVP = 0
IPPROTO_TCP = 0
IPPROTO_TP = 0
IPPROTO_UDP = 0
IPV6_CHECKSUM = 0
IPV6_DSTOPTS = 0
IPV6_HOPLIMIT = 0
IPV6_HOPOPTS = 0
IPV6_JOIN_GROUP = 0
IPV6_LEAVE_GROUP = 0
IPV6_MULTICAST_HOPS = 0
IPV6_MULTICAST_IF = 0
IPV6_MULTICAST_LOOP = 0
IPV6_NEXTHOP = 0
IPV6_PKTINFO = 0
IPV6_RECVDSTOPTS = 0
IPV6_RECVHOPLIMIT = 0
IPV6_RECVHOPOPTS = 0
IPV6_RECVPKTINFO = 0
IPV6_RECVRTHDR = 0
IPV6_RECVTCLASS = 0
IPV6_RTHDR = 0
IPV6_RTHDRDSTOPTS = 0
IPV6_RTHDR_TYPE_0 = 0
IPV6_TCLASS = 0
IPV6_UNICAST_HOPS = 0
IPV6_V6ONLY = 0
IP_ADD_MEMBERSHIP = 0
IP_DEFAULT_MULTICAST_LOOP = 0
IP_DEFAULT_MULTICAST_TTL = 0
IP_DROP_MEMBERSHIP = 0
IP_HDRINCL = 0
IP_MAX_MEMBERSHIPS = 0
IP_MULTICAST_IF = 0
IP_MULTICAST_LOOP = 0
IP_MULTICAST_TTL = 0
IP_OPTIONS = 0
IP_RECVOPTS = 0
IP_RECVRETOPTS = 0
IP_RETOPTS = 0
IP_TOS = 0
IP_TTL = 0
MSG_CTRUNC = 0
MSG_DONTROUTE = 0
MSG_DONTWAIT = 0
MSG_EOR = 0
MSG_OOB = 0
MSG_PEEK = 0
MSG_TRUNC = 0
MSG_WAITALL = 0
NETLINK_DNRTMSG = 0
NETLINK_FIREWALL = 0
NETLINK_IP6_FW = 0
NETLINK_NFLOG = 0
NETLINK_ROUTE = 0
NETLINK_USERSOCK = 0
NETLINK_XFRM = 0
NI_DGRAM = 0
NI_MAXHOST = 0
NI_MAXSERV = 0
NI_NAMEREQD = 0
NI_NOFQDN = 0
NI_NUMERICHOST = 0
NI_NUMERICSERV = 0
PACKET_BROADCAST = 0
PACKET_FASTROUTE = 0
PACKET_HOST = 0
PACKET_LOOPBACK = 0
PACKET_MULTICAST = 0
PACKET_OTHERHOST = 0
PACKET_OUTGOING = 0
PF_PACKET = 0
SHUT_RD = 0
SHUT_RDWR = 0
SHUT_WR = 0
SOL_HCI = 0
SOL_IP = 0
SOL_SOCKET = 0
SOL_TCP = 0
SOL_TIPC = 0
SOL_UDP = 0
SO_ACCEPTCONN = 0
SO_BROADCAST = 0
SO_DEBUG = 0
SO_DONTROUTE = 0
SO_ERROR = 0
SO_KEEPALIVE = 0
SO_LINGER = 0
SO_OOBINLINE = 0
SO_RCVBUF = 0
SO_RCVLOWAT = 0
SO_RCVTIMEO = 0
SO_REUSEADDR = 0
SO_SNDBUF = 0
SO_SNDLOWAT = 0
SO_SNDTIMEO = 0
SO_TYPE = 0
TCP_CORK = 0
TCP_DEFER_ACCEPT = 0
TCP_INFO = 0
TCP_KEEPCNT = 0
TCP_KEEPIDLE = 0
TCP_KEEPINTVL = 0
TCP_LINGER2 = 0
TCP_MAXSEG = 0
TCP_NODELAY = 0
TCP_QUICKACK = 0
TCP_SYNCNT = 0
TCP_WINDOW_CLAMP = 0
TIPC_ADDR_ID = 0
TIPC_ADDR_NAME = 0
TIPC_ADDR_NAMESEQ = 0
TIPC_CFG_SRV = 0
TIPC_CLUSTER_SCOPE = 0
TIPC_CONN_TIMEOUT = 0
TIPC_CRITICAL_IMPORTANCE = 0
TIPC_DEST_DROPPABLE = 0
TIPC_HIGH_IMPORTANCE = 0
TIPC_IMPORTANCE = 0
TIPC_LOW_IMPORTANCE = 0
TIPC_MEDIUM_IMPORTANCE = 0
TIPC_NODE_SCOPE = 0
TIPC_PUBLISHED = 0
TIPC_SRC_DROPPABLE = 0
TIPC_SUBSCR_TIMEOUT = 0
TIPC_SUB_CANCEL = 0
TIPC_SUB_PORTS = 0
TIPC_SUB_SERVICE = 0
TIPC_TOP_SRV = 0
TIPC_WAIT_FOREVER = 0
TIPC_WITHDRAWN = 0
TIPC_ZONE_SCOPE = 0


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


# ----- classes -----
class socket:
    family = 0
    type = 0
    proto = 0

    def __init__(self, family: int = ..., type: int = ...,
                 proto: int = ...) -> None: ...

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
    def recv(self, bufsize: int, flags: int = ...) -> str: ...

    # return type is an address
    def recvfrom(self, bufsize: int, flags: int = ...) -> Any: ...
    def recvfrom_into(self, buffer: str, nbytes: int,
                      flags: int = ...) -> Any: ...
    def recv_into(self, buffer: str, nbytes: int,
                  flags: int = ...) -> Any: ...
    def send(self, data: str, flags: int = ...) -> int: ...
    def sendall(self, data: str, flags: int = ...) -> None:
        ...  # return type: None on success
    @overload
    def sendto(self, data: str, address: Union[tuple, str]) -> int: ...
    @overload
    def sendto(self, data: str, flags: int, address: Union[tuple, str]) -> int: ...
    def setblocking(self, flag: bool) -> None: ...
    def settimeout(self, value: Union[float, None]) -> None: ...
    def setsockopt(self, level: int, optname: int, value: Union[int, bytes]) -> None: ...
    def shutdown(self, how: int) -> None: ...


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
def inet_aton(ip_string: str) -> str: ...  # ret val 4 bytes in length
def inet_ntoa(packed_ip: str) -> str: ...
def inet_pton(address_family: int, ip_string: str) -> str: ...
def inet_ntop(address_family: int, packed_ip: str) -> str: ...
def getdefaulttimeout() -> Optional[float]: ...
def setdefaulttimeout(timeout: Optional[float]) -> None: ...
