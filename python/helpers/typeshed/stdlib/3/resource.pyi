# Stubs for resource

# NOTE: These are incomplete!

from typing import Tuple, Optional, NamedTuple

RLIMIT_AS = ...  # type: int
RLIMIT_CORE = ...  # type: int
RLIMIT_CPU = ...  # type: int
RLIMIT_DATA = ...  # type: int
RLIMIT_FSIZE = ...  # type: int
RLIMIT_MEMLOCK = ...  # type: int
RLIMIT_MSGQUEUE = ...  # type: int
RLIMIT_NICE = ...  # type: int
RLIMIT_NOFILE = ...  # type: int
RLIMIT_NPROC = ...  # type: int
RLIMIT_OFILE = ...  # type: int
RLIMIT_RSS = ...  # type: int
RLIMIT_RTPRIO = ...  # type: int
RLIMIT_RTTIME = ...  # type: int
RLIMIT_SIGPENDING = ...  # type: int
RLIMIT_STACK = ...  # type: int
RLIM_INFINITY = ...  # type: int
RUSAGE_CHILDREN = ...  # type: int
RUSAGE_SELF = ...  # type: int
RUSAGE_THREAD = ...  # type: int

_RUsage = NamedTuple('_RUsage', [('ru_utime', float), ('ru_stime', float), ('ru_maxrss', int),
                                 ('ru_ixrss', int), ('ru_idrss', int), ('ru_isrss', int),
                                 ('ru_minflt', int), ('ru_majflt', int), ('ru_nswap', int),
                                 ('ru_inblock', int), ('ru_oublock', int), ('ru_msgsnd', int),
                                 ('ru_msgrcv', int), ('ru_nsignals', int), ('ru_nvcsw', int),
                                 ('ru_nivcsw', int)])

def getpagesize() -> int: ...
def getrlimit(resource: int) -> Tuple[int, int]: ...
def getrusage(who: int) -> _RUsage: ...
def prlimit(pid: int, resource: int, limits: Optional[Tuple[int, int]]) -> Tuple[int, int]: ...
def setrlimit(resource: int, limits: Tuple[int, int]) -> None: ...

# NOTE: This is an alias of OSError in Python 3.3.
class error(Exception): ...
