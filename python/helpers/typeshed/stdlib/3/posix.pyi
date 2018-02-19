# Stubs for posix

# NOTE: These are incomplete!

import sys
import typing
from typing import NamedTuple, Tuple

class stat_result:
    # For backward compatibility, the return value of stat() is also
    # accessible as a tuple of at least 10 integers giving the most important
    # (and portable) members of the stat structure, in the order st_mode,
    # st_ino, st_dev, st_nlink, st_uid, st_gid, st_size, st_atime, st_mtime,
    # st_ctime. More items may be added at the end by some implementations.

    st_mode: int  # protection bits,
    st_ino: int  # inode number,
    st_dev: int  # device,
    st_nlink: int  # number of hard links,
    st_uid: int  # user id of owner,
    st_gid: int  # group id of owner,
    st_size: int  # size of file, in bytes,
    st_atime: float  # time of most recent access,
    st_mtime: float  # time of most recent content modification,
    st_ctime: float  # platform dependent (time of most recent metadata change on Unix, or the time of creation on Windows)

    if sys.version_info >= (3, 3):
        st_atime_ns: int  # time of most recent access, in nanoseconds
        st_mtime_ns: int  # time of most recent content modification in nanoseconds
        st_ctime_ns: int  # platform dependent (time of most recent metadata change on Unix, or the time of creation on Windows) in nanoseconds

    # not documented
    def __init__(self, tuple: Tuple[int, ...]) -> None: ...

    # On some Unix systems (such as Linux), the following attributes may also
    # be available:
    st_blocks: int  # number of blocks allocated for file
    st_blksize: int  # filesystem blocksize
    st_rdev: int  # type of device if an inode device
    st_flags: int  # user defined flags for file

    # On other Unix systems (such as FreeBSD), the following attributes may be
    # available (but may be only filled out if root tries to use them):
    st_gen: int  # file generation number
    st_birthtime: int  # time of file creation

    # On Mac OS systems, the following attributes may also be available:
    st_rsize: int
    st_creator: int
    st_type: int

if sys.version_info >= (3, 3):
    uname_result = NamedTuple('uname_result', [('sysname', str), ('nodename', str),
        ('release', str), ('version', str), ('machine', str)])

    times_result = NamedTuple('times_result', [
        ('user', float),
        ('system', float),
        ('children_user', float),
        ('children_system', float),
        ('elapsed', float),
    ])

    waitid_result = NamedTuple('waitid_result', [
        ('si_pid', int),
        ('si_uid', int),
        ('si_signo', int),
        ('si_status', int),
        ('si_code', int),
    ])

    sched_param = NamedTuple('sched_priority', [
        ('sched_priority', int),
    ])
