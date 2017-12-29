# Stubs for posix

# NOTE: These are incomplete!

import sys
import typing
from os import stat_result
from typing import NamedTuple

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
