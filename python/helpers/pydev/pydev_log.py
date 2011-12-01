import logging
import os
import sys
from pydevd_constants import DebugInfoHolder

logger = logging.getLogger('pydev')
#hdlr = logging.FileHandler('/var/tmp/pycharm-debug-%d.log'%os.getpid())
#formatter = logging.Formatter('%(asctime)s %(levelname)s %(message)s')
#hdlr.setFormatter(formatter)
#logger.addHandler(hdlr)
logger.setLevel(logging.DEBUG)

def debug(message):
    logger.debug(message)
    if DebugInfoHolder.DEBUG_TRACE_LEVEL>2:
        sys.stderr.write(message)

def warn(message):
    logger.warn(message)
    if DebugInfoHolder.DEBUG_TRACE_LEVEL>1:
        sys.stderr.write(message)

def info(message):
    logger.info(message)
    sys.stderr.write(message)

def error(message):
    logger.error(message)
    sys.stderr.write(message)

