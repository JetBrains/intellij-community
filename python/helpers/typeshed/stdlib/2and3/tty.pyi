# Stubs for tty (Python 3.6)

# XXX: Undocumented integer constants
IFLAG = ...  # type: int
OFLAG = ...  # type: int
CFLAG = ...  # type: int
LFLAG = ...  # type: int
ISPEED = ...  # type: int
OSPEED = ...  # type: int
CC = ...  # type: int

def setraw(fd: int, when: int = ...) -> None: ...
def setcbreak(fd: int, when: int = ...) -> None: ...
