from typing import Tuple, Union, IO, Any, Optional, overload

AF_APPLETALK = ...  # type: int
AF_ASH = ...  # type: int
AF_ATMPVC = ...  # type: int
AF_ATMSVC = ...  # type: int
AF_AX25 = ...  # type: int
AF_BLUETOOTH = ...  # type: int
AF_BRIDGE = ...  # type: int
AF_DECnet = ...  # type: int
AF_ECONET = ...  # type: int
AF_INET = ...  # type: int
AF_INET6 = ...  # type: int
AF_IPX = ...  # type: int
AF_IRDA = ...  # type: int
AF_KEY = ...  # type: int
AF_LLC = ...  # type: int
AF_NETBEUI = ...  # type: int
AF_NETLINK = ...  # type: int
AF_NETROM = ...  # type: int
AF_PACKET = ...  # type: int
AF_PPPOX = ...  # type: int
AF_ROSE = ...  # type: int
AF_ROUTE = ...  # type: int
AF_SECURITY = ...  # type: int
AF_SNA = ...  # type: int
AF_TIPC = ...  # type: int
AF_UNIX = ...  # type: int
AF_UNSPEC = ...  # type: int
AF_WANPIPE = ...  # type: int
AF_X25 = ...  # type: int
AI_ADDRCONFIG = ...  # type: int
AI_ALL = ...  # type: int
AI_CANONNAME = ...  # type: int
AI_NUMERICHOST = ...  # type: int
AI_NUMERICSERV = ...  # type: int
AI_PASSIVE = ...  # type: int
AI_V4MAPPED = ...  # type: int
BDADDR_ANY = ...  # type: str
BDADDR_LOCAL = ...  # type: str
BTPROTO_HCI = ...  # type: int
BTPROTO_L2CAP = ...  # type: int
BTPROTO_RFCOMM = ...  # type: int
BTPROTO_SCO = ...  # type: int
EAI_ADDRFAMILY = ...  # type: int
EAI_AGAIN = ...  # type: int
EAI_BADFLAGS = ...  # type: int
EAI_FAIL = ...  # type: int
EAI_FAMILY = ...  # type: int
EAI_MEMORY = ...  # type: int
EAI_NODATA = ...  # type: int
EAI_NONAME = ...  # type: int
EAI_OVERFLOW = ...  # type: int
EAI_SERVICE = ...  # type: int
EAI_SOCKTYPE = ...  # type: int
EAI_SYSTEM = ...  # type: int
EBADF = ...  # type: int
EINTR = ...  # type: int
HCI_DATA_DIR = ...  # type: int
HCI_FILTER = ...  # type: int
HCI_TIME_STAMP = ...  # type: int
INADDR_ALLHOSTS_GROUP = ...  # type: int
INADDR_ANY = ...  # type: int
INADDR_BROADCAST = ...  # type: int
INADDR_LOOPBACK = ...  # type: int
INADDR_MAX_LOCAL_GROUP = ...  # type: int
INADDR_NONE = ...  # type: int
INADDR_UNSPEC_GROUP = ...  # type: int
IPPORT_RESERVED = ...  # type: int
IPPORT_USERRESERVED = ...  # type: int
IPPROTO_AH = ...  # type: int
IPPROTO_DSTOPTS = ...  # type: int
IPPROTO_EGP = ...  # type: int
IPPROTO_ESP = ...  # type: int
IPPROTO_FRAGMENT = ...  # type: int
IPPROTO_GRE = ...  # type: int
IPPROTO_HOPOPTS = ...  # type: int
IPPROTO_ICMP = ...  # type: int
IPPROTO_ICMPV6 = ...  # type: int
IPPROTO_IDP = ...  # type: int
IPPROTO_IGMP = ...  # type: int
IPPROTO_IP = ...  # type: int
IPPROTO_IPIP = ...  # type: int
IPPROTO_IPV6 = ...  # type: int
IPPROTO_NONE = ...  # type: int
IPPROTO_PIM = ...  # type: int
IPPROTO_PUP = ...  # type: int
IPPROTO_RAW = ...  # type: int
IPPROTO_ROUTING = ...  # type: int
IPPROTO_RSVP = ...  # type: int
IPPROTO_TCP = ...  # type: int
IPPROTO_TP = ...  # type: int
IPPROTO_UDP = ...  # type: int
IPV6_CHECKSUM = ...  # type: int
IPV6_DSTOPTS = ...  # type: int
IPV6_HOPLIMIT = ...  # type: int
IPV6_HOPOPTS = ...  # type: int
IPV6_JOIN_GROUP = ...  # type: int
IPV6_LEAVE_GROUP = ...  # type: int
IPV6_MULTICAST_HOPS = ...  # type: int
IPV6_MULTICAST_IF = ...  # type: int
IPV6_MULTICAST_LOOP = ...  # type: int
IPV6_NEXTHOP = ...  # type: int
IPV6_PKTINFO = ...  # type: int
IPV6_RECVDSTOPTS = ...  # type: int
IPV6_RECVHOPLIMIT = ...  # type: int
IPV6_RECVHOPOPTS = ...  # type: int
IPV6_RECVPKTINFO = ...  # type: int
IPV6_RECVRTHDR = ...  # type: int
IPV6_RECVTCLASS = ...  # type: int
IPV6_RTHDR = ...  # type: int
IPV6_RTHDRDSTOPTS = ...  # type: int
IPV6_RTHDR_TYPE_0 = ...  # type: int
IPV6_TCLASS = ...  # type: int
IPV6_UNICAST_HOPS = ...  # type: int
IPV6_V6ONLY = ...  # type: int
IP_ADD_MEMBERSHIP = ...  # type: int
IP_DEFAULT_MULTICAST_LOOP = ...  # type: int
IP_DEFAULT_MULTICAST_TTL = ...  # type: int
IP_DROP_MEMBERSHIP = ...  # type: int
IP_HDRINCL = ...  # type: int
IP_MAX_MEMBERSHIPS = ...  # type: int
IP_MULTICAST_IF = ...  # type: int
IP_MULTICAST_LOOP = ...  # type: int
IP_MULTICAST_TTL = ...  # type: int
IP_OPTIONS = ...  # type: int
IP_RECVOPTS = ...  # type: int
IP_RECVRETOPTS = ...  # type: int
IP_RETOPTS = ...  # type: int
IP_TOS = ...  # type: int
IP_TTL = ...  # type: int
MSG_CTRUNC = ...  # type: int
MSG_DONTROUTE = ...  # type: int
MSG_DONTWAIT = ...  # type: int
MSG_EOR = ...  # type: int
MSG_OOB = ...  # type: int
MSG_PEEK = ...  # type: int
MSG_TRUNC = ...  # type: int
MSG_WAITALL = ...  # type: int
MethodType = ...  # type: type
NETLINK_DNRTMSG = ...  # type: int
NETLINK_FIREWALL = ...  # type: int
NETLINK_IP6_FW = ...  # type: int
NETLINK_NFLOG = ...  # type: int
NETLINK_ROUTE = ...  # type: int
NETLINK_USERSOCK = ...  # type: int
NETLINK_XFRM = ...  # type: int
NI_DGRAM = ...  # type: int
NI_MAXHOST = ...  # type: int
NI_MAXSERV = ...  # type: int
NI_NAMEREQD = ...  # type: int
NI_NOFQDN = ...  # type: int
NI_NUMERICHOST = ...  # type: int
NI_NUMERICSERV = ...  # type: int
PACKET_BROADCAST = ...  # type: int
PACKET_FASTROUTE = ...  # type: int
PACKET_HOST = ...  # type: int
PACKET_LOOPBACK = ...  # type: int
PACKET_MULTICAST = ...  # type: int
PACKET_OTHERHOST = ...  # type: int
PACKET_OUTGOING = ...  # type: int
PF_PACKET = ...  # type: int
SHUT_RD = ...  # type: int
SHUT_RDWR = ...  # type: int
SHUT_WR = ...  # type: int
SOCK_DGRAM = ...  # type: int
SOCK_RAW = ...  # type: int
SOCK_RDM = ...  # type: int
SOCK_SEQPACKET = ...  # type: int
SOCK_STREAM = ...  # type: int
SOL_HCI = ...  # type: int
SOL_IP = ...  # type: int
SOL_SOCKET = ...  # type: int
SOL_TCP = ...  # type: int
SOL_TIPC = ...  # type: int
SOL_UDP = ...  # type: int
SOMAXCONN = ...  # type: int
SO_ACCEPTCONN = ...  # type: int
SO_BROADCAST = ...  # type: int
SO_DEBUG = ...  # type: int
SO_DONTROUTE = ...  # type: int
SO_ERROR = ...  # type: int
SO_KEEPALIVE = ...  # type: int
SO_LINGER = ...  # type: int
SO_OOBINLINE = ...  # type: int
SO_RCVBUF = ...  # type: int
SO_RCVLOWAT = ...  # type: int
SO_RCVTIMEO = ...  # type: int
SO_REUSEADDR = ...  # type: int
SO_REUSEPORT = ...  # type: int
SO_SNDBUF = ...  # type: int
SO_SNDLOWAT = ...  # type: int
SO_SNDTIMEO = ...  # type: int
SO_TYPE = ...  # type: int
SSL_ERROR_EOF = ...  # type: int
SSL_ERROR_INVALID_ERROR_CODE = ...  # type: int
SSL_ERROR_SSL = ...  # type: int
SSL_ERROR_SYSCALL = ...  # type: int
SSL_ERROR_WANT_CONNECT = ...  # type: int
SSL_ERROR_WANT_READ = ...  # type: int
SSL_ERROR_WANT_WRITE = ...  # type: int
SSL_ERROR_WANT_X509_LOOKUP = ...  # type: int
SSL_ERROR_ZERO_RETURN = ...  # type: int
TCP_CORK = ...  # type: int
TCP_DEFER_ACCEPT = ...  # type: int
TCP_INFO = ...  # type: int
TCP_KEEPCNT = ...  # type: int
TCP_KEEPIDLE = ...  # type: int
TCP_KEEPINTVL = ...  # type: int
TCP_LINGER2 = ...  # type: int
TCP_MAXSEG = ...  # type: int
TCP_NODELAY = ...  # type: int
TCP_QUICKACK = ...  # type: int
TCP_SYNCNT = ...  # type: int
TCP_WINDOW_CLAMP = ...  # type: int
TIPC_ADDR_ID = ...  # type: int
TIPC_ADDR_NAME = ...  # type: int
TIPC_ADDR_NAMESEQ = ...  # type: int
TIPC_CFG_SRV = ...  # type: int
TIPC_CLUSTER_SCOPE = ...  # type: int
TIPC_CONN_TIMEOUT = ...  # type: int
TIPC_CRITICAL_IMPORTANCE = ...  # type: int
TIPC_DEST_DROPPABLE = ...  # type: int
TIPC_HIGH_IMPORTANCE = ...  # type: int
TIPC_IMPORTANCE = ...  # type: int
TIPC_LOW_IMPORTANCE = ...  # type: int
TIPC_MEDIUM_IMPORTANCE = ...  # type: int
TIPC_NODE_SCOPE = ...  # type: int
TIPC_PUBLISHED = ...  # type: int
TIPC_SRC_DROPPABLE = ...  # type: int
TIPC_SUBSCR_TIMEOUT = ...  # type: int
TIPC_SUB_CANCEL = ...  # type: int
TIPC_SUB_PORTS = ...  # type: int
TIPC_SUB_SERVICE = ...  # type: int
TIPC_TOP_SRV = ...  # type: int
TIPC_WAIT_FOREVER = ...  # type: int
TIPC_WITHDRAWN = ...  # type: int
TIPC_ZONE_SCOPE = ...  # type: int

# PyCapsule
CAPI = ...  # type: Any

has_ipv6 = ...  # type: bool

class error(IOError): ...
class gaierror(error): ...
class timeout(error): ...

class SocketType(object):
    family = ...  # type: int
    type = ...  # type: int
    proto = ...  # type: int
    timeout = ...  # type: float

    def __init__(self, family: int = ..., type: int = ..., proto: int = ...) -> None: ...
    def accept(self) -> Tuple['SocketType', tuple]: ...
    def bind(self, address: tuple) -> None: ...
    def close(self) -> None: ...
    def connect(self, address: tuple) -> None:
        raise gaierror
        raise timeout
    def connect_ex(self, address: tuple) -> int: ...
    def dup(self) -> "SocketType": ...
    def fileno(self) -> int: ...
    def getpeername(self) -> tuple: ...
    def getsockname(self) -> tuple: ...
    def getsockopt(self, level: int, option: int, buffersize: int = ...) -> str: ...
    def gettimeout(self) -> float: ...
    def listen(self, backlog: int) -> None:
        raise error
    def makefile(self, mode: str = ..., buffersize: int = ...) -> IO[Any]: ...
    def recv(self, buffersize: int, flags: int = ...) -> str: ...
    def recv_into(self, buffer: bytearray, nbytes: int = ..., flags: int = ...) -> int: ...
    def recvfrom(self, buffersize: int, flags: int = ...) -> tuple:
        raise error
    def recvfrom_into(self, buffer: bytearray, nbytes: int = ...,
                      flags: int = ...) -> int: ...
    def send(self, data: str, flags: int =...) -> int: ...
    def sendall(self, data: str, flags: int = ...) -> None: ...
    @overload
    def sendto(self, data: str, address: tuple) -> int: ...
    @overload
    def sendto(self, data: str, flags: int, address: tuple) -> int: ...
    def setblocking(self, flag: bool) -> None: ...
    def setsockopt(self, level: int, option: int, value: Union[int, str]) -> None: ...
    def settimeout(self, value: Optional[float]) -> None: ...
    def shutdown(self, flag: int) -> None: ...
