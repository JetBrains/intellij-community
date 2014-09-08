import sys
from pydevd_constants import DebugInfoHolder
from pydevd_constants import DictContains

import traceback

WARN_ONCE_MAP = {}

def stderr_write(message):
    sys.stderr.write(message)
    sys.stderr.write("\n")


def debug(message):
    if DebugInfoHolder.DEBUG_TRACE_LEVEL>2:
        stderr_write(message)


def warn(message):
    if DebugInfoHolder.DEBUG_TRACE_LEVEL>1:
        stderr_write(message)


def info(message):
    stderr_write(message)


def error(message, tb=False):
    stderr_write(message)
    if tb:
        traceback.print_exc()


def error_once(message):
    if not DictContains(WARN_ONCE_MAP, message):
        WARN_ONCE_MAP[message] = True
        error(message)

