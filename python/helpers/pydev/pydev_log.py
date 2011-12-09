import os
import sys
from pydevd_constants import DebugInfoHolder

def debug(message):
    if DebugInfoHolder.DEBUG_TRACE_LEVEL>2:
        sys.stderr.write(message)

def warn(message):
    if DebugInfoHolder.DEBUG_TRACE_LEVEL>1:
        sys.stderr.write(message)

def info(message):
    sys.stderr.write(message)

def error(message):
    sys.stderr.write(message)

