import sys
from _pydevd_bundle.pydevd_constants import DebugInfoHolder
from _pydev_imps._pydev_saved_modules import threading
currentThread = threading.currentThread


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
    if message not in WARN_ONCE_MAP:
        WARN_ONCE_MAP[message] = True
        error(message)

