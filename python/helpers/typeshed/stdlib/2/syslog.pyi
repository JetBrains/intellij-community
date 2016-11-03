LOG_ALERT = ...  # type: int
LOG_AUTH = ...  # type: int
LOG_CONS = ...  # type: int
LOG_CRIT = ...  # type: int
LOG_CRON = ...  # type: int
LOG_DAEMON = ...  # type: int
LOG_DEBUG = ...  # type: int
LOG_EMERG = ...  # type: int
LOG_ERR = ...  # type: int
LOG_INFO = ...  # type: int
LOG_KERN = ...  # type: int
LOG_LOCAL0 = ...  # type: int
LOG_LOCAL1 = ...  # type: int
LOG_LOCAL2 = ...  # type: int
LOG_LOCAL3 = ...  # type: int
LOG_LOCAL4 = ...  # type: int
LOG_LOCAL5 = ...  # type: int
LOG_LOCAL6 = ...  # type: int
LOG_LOCAL7 = ...  # type: int
LOG_LPR = ...  # type: int
LOG_MAIL = ...  # type: int
LOG_NDELAY = ...  # type: int
LOG_NEWS = ...  # type: int
LOG_NOTICE = ...  # type: int
LOG_NOWAIT = ...  # type: int
LOG_PERROR = ...  # type: int
LOG_PID = ...  # type: int
LOG_SYSLOG = ...  # type: int
LOG_USER = ...  # type: int
LOG_UUCP = ...  # type: int
LOG_WARNING = ...  # type: int

def LOG_MASK(a: int) -> int: ...
def LOG_UPTO(a: int) -> int: ...
def closelog() -> None: ...
def openlog(ident: str = ..., logoption: int = ..., facility: int = ...) -> None: ...
def setlogmask(x: int) -> int: ...
def syslog(priority: int, message: str) -> None: ...
