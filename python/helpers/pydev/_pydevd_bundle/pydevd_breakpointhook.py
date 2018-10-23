
# This file shouldn't be added to DONT_TRACE dict

import sys
import os


def breakpointhook(*args, **kwargs):
    # It's necessary to use inner function to add extra frame
    def breakpoint():
        hookname = os.getenv('PYTHONBREAKPOINT')
        if hookname is not None and len(hookname) > 0 and hasattr(sys, '__breakpointhook__'):
            sys.__breakpointhook__(*args, **kwargs)
        else:
            import pydevd
            py_db = pydevd.get_global_debugger()
            if (py_db is not None) and (py_db.frame_eval_func is not None):
                from _pydevd_frame_eval.pydevd_frame_tracing import suspend_at_builtin_breakpoint
                suspend_at_builtin_breakpoint()
            else:
                pydevd.settrace(
                    suspend=True,
                    trace_only_current_thread=True,
                    patch_multiprocessing=False,
                    stop_at_frame=sys._getframe(),
                )
    breakpoint()


def install_breakpointhook(pydevd_breakpointhook=None):
    if pydevd_breakpointhook is None:
        pydevd_breakpointhook = breakpointhook
    if sys.version_info[0:2] >= (3, 7):
        # There are some choices on how to provide the breakpoint hook. Namely, we can provide a
        # PYTHONBREAKPOINT which provides the import path for a method to be executed or we
        # can override sys.breakpointhook.
        # pydevd overrides sys.breakpointhook instead of providing an environment variable because
        # it's possible that the debugger starts the user program but is not available in the
        # PYTHONPATH (and would thus fail to be imported if PYTHONBREAKPOINT was set to pydevd.settrace).
        # Note that the implementation still takes PYTHONBREAKPOINT in account (so, if it was provided
        # by someone else, it'd still work).
        sys.breakpointhook = pydevd_breakpointhook
