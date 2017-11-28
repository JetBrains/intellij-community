# Stubs for posix

# NOTE: These are incomplete!

import sys
import typing
from os import stat_result
from typing import NamedTuple

if sys.version_info >= (3, 3):
    uname_result = NamedTuple('uname_result', [('sysname', str), ('nodename', str),
        ('release', str), ('version', str), ('machine', str)])
