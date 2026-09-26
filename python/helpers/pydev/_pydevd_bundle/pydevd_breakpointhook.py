
# This file shouldn't be added to DONT_TRACE dict

import sys
import os


def breakpointhook(*args, **kwargs):
    # It's necessary to use inner function to add extra frame
    def pydevd_breakpointhook():
        hookname = os.getenv('PYTHONBREAKPOINT')
        if hookname is not None and len(hookname) > 0 and hasattr(sys, '__breakpointhook__'):
            sys.__breakpointhook__(*args, **kwargs)
        else:
            import pydevd
            py_db = pydevd.get_global_debugger()
            if py_db is not None:
                from _pydevd_frame_eval.pydevd_frame_tracing import suspend_at_builtin_breakpoint
                suspend_at_builtin_breakpoint()
            else:
                pydevd.settrace(
                    suspend=True,
                    trace_only_current_thread=True,
                    patch_multiprocessing=False,
                    stop_at_frame=sys._getframe().f_back.f_back,
                )
    pydevd_breakpointhook()
