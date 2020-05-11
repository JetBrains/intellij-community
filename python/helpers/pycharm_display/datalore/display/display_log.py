#  Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import os
import sys

SHOW_DEBUG_INFO = False


def debug(message):
    check_if_debugging_enabled()
    if SHOW_DEBUG_INFO:
        sys.stderr.write(message)
        sys.stderr.write("\n")


def check_if_debugging_enabled():
    # Important to check current env variables on every message because of
    # use case with executing different configurations in the same Python Console session
    global SHOW_DEBUG_INFO
    if not SHOW_DEBUG_INFO:
        SHOW_DEBUG_INFO = os.getenv('PYCHARM_DEBUG', 'False').lower() in ['true', '1']
        if SHOW_DEBUG_INFO:
            debug("=== Scientific View debug enabled ===")


check_if_debugging_enabled()
