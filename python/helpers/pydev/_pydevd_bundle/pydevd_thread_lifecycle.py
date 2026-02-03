#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import threading

from _pydevd_bundle import pydevd_utils
from _pydevd_bundle.pydevd_additional_thread_info import set_additional_thread_info
from _pydevd_bundle.pydevd_comm_constants import CMD_STEP_INTO, CMD_THREAD_SUSPEND
from _pydevd_bundle.pydevd_constants import PYTHON_SUSPEND, STATE_SUSPEND, USE_LOW_IMPACT_MONITORING
from _pydev_bundle import pydev_log
import sys

def mark_thread_suspended(thread, stop_reason):
    info = set_additional_thread_info(thread)
    info.suspend_type = PYTHON_SUSPEND
    thread.stop_reason = stop_reason
    if info.pydev_step_cmd == -1:
        # If the step command is not specified, set it to step into
        # to make sure it'll break as soon as possible.
        info.pydev_step_cmd = CMD_STEP_INTO

    # Mark as suspend as the last thing.
    info.pydev_state = STATE_SUSPEND

    return info


def suspend_all_threads(py_db, except_thread):
    """
    Suspend all except the one passed as a parameter.
    :param except_thread:
    """
    # if USE_LOW_IMPACT_MONITORING:
    #     pydevd_sys_monitoring.update_monitor_events(suspend_requested=True)

    pydev_log.info("Suspending all threads except: %s" % except_thread)
    all_threads = pydevd_utils.get_non_pydevd_threads()
    for t in all_threads:
        if getattr(t, "pydev_do_not_trace", None):
            pass  # skip some other threads, i.e. ipython history saving thread from debug console
        else:
            if t is except_thread:
                continue
            info = mark_thread_suspended(t, CMD_THREAD_SUSPEND)
            frame = info.get_topmost_frame(t)

            # Reset the tracing as in this case as it could've set scopes to be untraced.
            if frame is not None:
                try:
                    py_db.set_trace_for_frame_and_parents(frame)
                finally:
                    frame = None

    if USE_LOW_IMPACT_MONITORING:
        # After suspending the frames we need the monitoring to be reset.
        try:
            sys.monitoring.restart_events()
        except:
            pass
