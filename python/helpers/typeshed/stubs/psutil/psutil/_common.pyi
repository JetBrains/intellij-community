import enum
from typing import Any, NamedTuple

POSIX: Any
WINDOWS: Any
LINUX: Any
MACOS: Any
OSX: Any
FREEBSD: Any
OPENBSD: Any
NETBSD: Any
BSD: Any
SUNOS: Any
AIX: Any
STATUS_RUNNING: str
STATUS_SLEEPING: str
STATUS_DISK_SLEEP: str
STATUS_STOPPED: str
STATUS_TRACING_STOP: str
STATUS_ZOMBIE: str
STATUS_DEAD: str
STATUS_WAKE_KILL: str
STATUS_WAKING: str
STATUS_IDLE: str
STATUS_LOCKED: str
STATUS_WAITING: str
STATUS_SUSPENDED: str
STATUS_PARKED: str
CONN_ESTABLISHED: str
CONN_SYN_SENT: str
CONN_SYN_RECV: str
CONN_FIN_WAIT1: str
CONN_FIN_WAIT2: str
CONN_TIME_WAIT: str
CONN_CLOSE: str
CONN_CLOSE_WAIT: str
CONN_LAST_ACK: str
CONN_LISTEN: str
CONN_CLOSING: str
CONN_NONE: str
NIC_DUPLEX_FULL: int
NIC_DUPLEX_HALF: int
NIC_DUPLEX_UNKNOWN: int

class NicDuplex(enum.IntEnum):
    NIC_DUPLEX_FULL: int
    NIC_DUPLEX_HALF: int
    NIC_DUPLEX_UNKNOWN: int

POWER_TIME_UNKNOWN: int
POWER_TIME_UNLIMITED: int

class BatteryTime(enum.IntEnum):
    POWER_TIME_UNKNOWN: int
    POWER_TIME_UNLIMITED: int

ENCODING: Any
ENCODING_ERRS: Any

class sswap(NamedTuple):
    total: Any
    used: Any
    free: Any
    percent: Any
    sin: Any
    sout: Any

class sdiskusage(NamedTuple):
    total: Any
    used: Any
    free: Any
    percent: Any

class sdiskio(NamedTuple):
    read_count: Any
    write_count: Any
    read_bytes: Any
    write_bytes: Any
    read_time: Any
    write_time: Any

class sdiskpart(NamedTuple):
    device: Any
    mountpoint: Any
    fstype: Any
    opts: Any
    maxfile: Any
    maxpath: Any

class snetio(NamedTuple):
    bytes_sent: Any
    bytes_recv: Any
    packets_sent: Any
    packets_recv: Any
    errin: Any
    errout: Any
    dropin: Any
    dropout: Any

class suser(NamedTuple):
    name: Any
    terminal: Any
    host: Any
    started: Any
    pid: Any

class sconn(NamedTuple):
    fd: Any
    family: Any
    type: Any
    laddr: Any
    raddr: Any
    status: Any
    pid: Any

class snicaddr(NamedTuple):
    family: Any
    address: Any
    netmask: Any
    broadcast: Any
    ptp: Any

class snicstats(NamedTuple):
    isup: Any
    duplex: Any
    speed: Any
    mtu: Any

class scpustats(NamedTuple):
    ctx_switches: Any
    interrupts: Any
    soft_interrupts: Any
    syscalls: Any

class scpufreq(NamedTuple):
    current: Any
    min: Any
    max: Any

class shwtemp(NamedTuple):
    label: Any
    current: Any
    high: Any
    critical: Any

class sbattery(NamedTuple):
    percent: Any
    secsleft: Any
    power_plugged: Any

class sfan(NamedTuple):
    label: Any
    current: Any

class pcputimes(NamedTuple):
    user: Any
    system: Any
    children_user: Any
    children_system: Any

class popenfile(NamedTuple):
    path: Any
    fd: Any

class pthread(NamedTuple):
    id: Any
    user_time: Any
    system_time: Any

class puids(NamedTuple):
    real: Any
    effective: Any
    saved: Any

class pgids(NamedTuple):
    real: Any
    effective: Any
    saved: Any

class pio(NamedTuple):
    read_count: Any
    write_count: Any
    read_bytes: Any
    write_bytes: Any

class pionice(NamedTuple):
    ioclass: Any
    value: Any

class pctxsw(NamedTuple):
    voluntary: Any
    involuntary: Any

class pconn(NamedTuple):
    fd: Any
    family: Any
    type: Any
    laddr: Any
    raddr: Any
    status: Any

class addr(NamedTuple):
    ip: Any
    port: Any

conn_tmap: Any

class Error(Exception):
    __module__: str
    msg: Any
    def __init__(self, msg: str = ...) -> None: ...

class NoSuchProcess(Error):
    __module__: str
    pid: Any
    name: Any
    msg: Any
    def __init__(self, pid, name: Any | None = ..., msg: Any | None = ...) -> None: ...

class ZombieProcess(NoSuchProcess):
    __module__: str
    pid: Any
    ppid: Any
    name: Any
    msg: Any
    def __init__(self, pid, name: Any | None = ..., ppid: Any | None = ..., msg: Any | None = ...) -> None: ...

class AccessDenied(Error):
    __module__: str
    pid: Any
    name: Any
    msg: Any
    def __init__(self, pid: Any | None = ..., name: Any | None = ..., msg: Any | None = ...) -> None: ...

class TimeoutExpired(Error):
    __module__: str
    seconds: Any
    pid: Any
    name: Any
    def __init__(self, seconds, pid: Any | None = ..., name: Any | None = ...) -> None: ...

def usage_percent(used, total, round_: Any | None = ...): ...
def memoize(fun): ...
def isfile_strict(path): ...
def path_exists_strict(path): ...
def supports_ipv6(): ...
def parse_environ_block(data): ...
def sockfam_to_enum(num): ...
def socktype_to_enum(num): ...
def conn_to_ntuple(fd, fam, type_, laddr, raddr, status, status_map, pid: Any | None = ...): ...
def deprecated_method(replacement): ...

class _WrapNumbers:
    lock: Any
    cache: Any
    reminders: Any
    reminder_keys: Any
    def __init__(self) -> None: ...
    def run(self, input_dict, name): ...
    def cache_clear(self, name: Any | None = ...) -> None: ...
    def cache_info(self): ...

def wrap_numbers(input_dict, name): ...
def bytes2human(n, format: str = ...): ...
def term_supports_colors(file=...): ...
def hilite(s, color: Any | None = ..., bold: bool = ...): ...
def print_color(s, color: Any | None = ..., bold: bool = ..., file=...) -> None: ...
def debug(msg) -> None: ...
