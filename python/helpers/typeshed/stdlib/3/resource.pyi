# Stubs for resource

# NOTE: These are incomplete!

from typing import Tuple

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

def getrlimit(resource: int) -> Tuple[int, int]: ...
def setrlimit(resource: int, limits: Tuple[int, int]) -> None: ...

# NOTE: This is an alias of OSError in Python 3.3.
class error(Exception): ...
